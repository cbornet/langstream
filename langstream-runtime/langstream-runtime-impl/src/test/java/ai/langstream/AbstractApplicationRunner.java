/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.langstream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.langstream.api.model.Application;
import ai.langstream.api.runtime.ClusterRuntimeRegistry;
import ai.langstream.api.runtime.ExecutionPlan;
import ai.langstream.api.runtime.PluginsRegistry;
import ai.langstream.deployer.k8s.agents.AgentResourcesFactory;
import ai.langstream.impl.deploy.ApplicationDeployer;
import ai.langstream.impl.k8s.tests.KubeTestServer;
import ai.langstream.impl.parser.ModelBuilder;
import ai.langstream.kafka.extensions.KafkaContainerExtension;
import ai.langstream.runtime.agent.AgentRunner;
import ai.langstream.runtime.agent.api.AgentInfo;
import ai.langstream.runtime.api.agent.RuntimePodConfiguration;
import io.fabric8.kubernetes.api.model.Secret;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

@Slf4j
public abstract class AbstractApplicationRunner {

    public static final Path agentsDirectory;

    static {
        agentsDirectory = Path.of(System.getProperty("user.dir"), "target", "agents");
        log.info("Agents directory is {}", agentsDirectory);
    }

    @RegisterExtension protected static final KubeTestServer kubeServer = new KubeTestServer();

    @RegisterExtension
    protected static final KafkaContainerExtension kafkaContainer = new KafkaContainerExtension();

    protected static ApplicationDeployer applicationDeployer;

    protected record ApplicationRuntime(
            String tenant,
            String applicationId,
            Application applicationInstance,
            ExecutionPlan implementation,
            Map<String, Secret> secrets)
            implements AutoCloseable {
        public void close() {
            applicationDeployer.delete(tenant, implementation, null);
            Awaitility.await()
                    .until(
                            () -> {
                                log.info("Waiting for secrets to be deleted. {}", secrets);
                                return secrets.isEmpty();
                            });
            // this is a workaround, we want to clean up the env
            applicationDeployer.deleteStreamingClusterResourcesForTests(tenant, implementation);
        }
    }

    protected ApplicationRuntime deployApplication(
            String tenant,
            String appId,
            Map<String, String> application,
            String instance,
            String... expectedAgents)
            throws Exception {

        kubeServer.spyAgentCustomResources(tenant, expectedAgents);
        final Map<String, Secret> secrets =
                kubeServer.spyAgentCustomResourcesSecrets(tenant, expectedAgents);

        Application applicationInstance =
                ModelBuilder.buildApplicationInstance(application, instance, null).getApplication();

        ExecutionPlan implementation =
                applicationDeployer.createImplementation(appId, applicationInstance);

        applicationDeployer.deploy(tenant, implementation, null);

        return new ApplicationRuntime(tenant, appId, applicationInstance, implementation, secrets);
    }

    protected String buildInstanceYaml() {
        return """
                instance:
                  streamingCluster:
                    type: "kafka"
                    configuration:
                      admin:
                        bootstrap.servers: "%s"
                  computeCluster:
                     type: "kubernetes"
                """
                .formatted(kafkaContainer.getBootstrapServers());
    }

    @BeforeAll
    public static void setup() {
        applicationDeployer =
                ApplicationDeployer.builder()
                        .registry(new ClusterRuntimeRegistry())
                        .pluginsRegistry(new PluginsRegistry())
                        .build();
    }

    protected KafkaProducer<String, String> createProducer() {
        return new KafkaProducer<>(
                Map.of(
                        "bootstrap.servers",
                        kafkaContainer.getBootstrapServers(),
                        "key.serializer",
                        "org.apache.kafka.common.serialization.StringSerializer",
                        "value.serializer",
                        "org.apache.kafka.common.serialization.StringSerializer"));
    }

    protected void sendMessage(String topic, Object content, KafkaProducer producer)
            throws Exception {
        sendMessage(topic, content, List.of(), producer);
    }

    protected void sendMessage(
            String topic, Object content, List<Header> headers, KafkaProducer producer)
            throws Exception {
        producer.send(
                        new ProducerRecord<>(
                                topic, null, System.currentTimeMillis(), "key", content, headers))
                .get();
        producer.flush();
    }

    protected List<ConsumerRecord> waitForMessages(KafkaConsumer consumer, List<Object> expected) {
        List<ConsumerRecord> result = new ArrayList<>();
        List<Object> received = new ArrayList<>();

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            ConsumerRecords<String, String> poll =
                                    consumer.poll(Duration.ofSeconds(2));
                            for (ConsumerRecord record : poll) {
                                log.info("Received message {}", record);
                                received.add(record.value());
                                result.add(record);
                            }
                            log.info("Result: {}", received);
                            received.forEach(r -> log.info("Received |{}|", r));

                            assertEquals(expected.size(), received.size());
                            for (int i = 0; i < expected.size(); i++) {
                                Object expectedValue = expected.get(i);
                                Object actualValue = received.get(i);
                                if (expectedValue instanceof byte[]) {
                                    assertArrayEquals((byte[]) expectedValue, (byte[]) actualValue);
                                } else {
                                    assertEquals(expectedValue, actualValue);
                                }
                            }
                        });

        return result;
    }

    protected List<ConsumerRecord> waitForMessagesInAnyOrder(
            KafkaConsumer consumer, Set<String> expected) {
        List<ConsumerRecord> result = new ArrayList<>();
        List<Object> received = new ArrayList<>();

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            ConsumerRecords<String, String> poll =
                                    consumer.poll(Duration.ofSeconds(2));
                            for (ConsumerRecord record : poll) {
                                log.info("Received message {}", record);
                                received.add(record.value());
                                result.add(record);
                            }
                            log.info("Result: {}", received);
                            received.forEach(r -> log.info("Received |{}|", r));

                            assertEquals(expected.size(), received.size());
                            for (Object expectedValue : expected) {
                                // this doesn't work for byte[]
                                assertFalse(expectedValue instanceof byte[]);
                                assertTrue(
                                        received.contains(expectedValue),
                                        "Expected value "
                                                + expectedValue
                                                + " not found in "
                                                + received);
                            }
                        });

        return result;
    }

    public record AgentRunResult(Map<String, AgentInfo> info) {}

    protected AgentRunResult executeAgentRunners(ApplicationRuntime runtime) throws Exception {
        String runnerExecutionId = UUID.randomUUID().toString();
        log.info(
                "{} Starting Agent Runners. Running {} pods",
                runnerExecutionId,
                runtime.secrets.size());
        Map<String, AgentInfo> allAgentsInfo = new HashMap<>();
        try {
            List<RuntimePodConfiguration> pods = new ArrayList<>();
            runtime.secrets()
                    .forEach(
                            (key, secret) -> {
                                RuntimePodConfiguration runtimePodConfiguration =
                                        AgentResourcesFactory.readRuntimePodConfigurationFromSecret(
                                                secret);
                                log.info(
                                        "{} Pod configuration {} = {}",
                                        runnerExecutionId,
                                        key,
                                        runtimePodConfiguration);
                                pods.add(runtimePodConfiguration);
                            });
            // execute all the pods
            ExecutorService executorService = Executors.newCachedThreadPool();
            List<CompletableFuture> futures = new ArrayList<>();
            for (RuntimePodConfiguration podConfiguration : pods) {
                CompletableFuture<?> handle = new CompletableFuture<>();
                futures.add(handle);
                executorService.submit(
                        () -> {
                            String originalName = Thread.currentThread().getName();
                            Thread.currentThread()
                                    .setName(
                                            podConfiguration.agent().agentId()
                                                    + "runner-tid-"
                                                    + runnerExecutionId);
                            try {
                                log.info(
                                        "{} AgentPod {} Started",
                                        runnerExecutionId,
                                        podConfiguration.agent().agentId());
                                AgentInfo agentInfo = new AgentInfo();
                                allAgentsInfo.put(podConfiguration.agent().agentId(), agentInfo);
                                AgentRunner.runAgent(
                                        podConfiguration,
                                        null,
                                        null,
                                        agentsDirectory,
                                        agentInfo,
                                        10);
                                List<?> infos = agentInfo.serveWorkerStatus();
                                log.info(
                                        "{} AgentPod {} AgentInfo {}",
                                        runnerExecutionId,
                                        podConfiguration.agent().agentId(),
                                        infos);
                                handle.complete(null);
                            } catch (Throwable error) {
                                log.error(
                                        "{} Error on AgentPod {}",
                                        runnerExecutionId,
                                        podConfiguration.agent().agentId(),
                                        error);
                                handle.completeExceptionally(error);
                            } finally {
                                log.info(
                                        "{} AgentPod {} finished",
                                        runnerExecutionId,
                                        podConfiguration.agent().agentId());
                                Thread.currentThread().setName(originalName);
                            }
                        });
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            } catch (ExecutionException executionException) {
                // unwrap the exception in order to easily perform assertions
                if (executionException instanceof Exception) {
                    throw (Exception) executionException.getCause();
                } else {
                    throw executionException;
                }
            }
            executorService.shutdown();
            assertTrue(
                    executorService.awaitTermination(1, TimeUnit.MINUTES),
                    "the pods didn't finish in time");
        } finally {
            log.info("{} Agent Runners Stopped", runnerExecutionId);
        }
        return new AgentRunResult(allAgentsInfo);
    }

    protected KafkaConsumer<String, String> createConsumer(String topic) {
        KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(
                        Map.of(
                                "bootstrap.servers",
                                kafkaContainer.getBootstrapServers(),
                                "key.deserializer",
                                "org.apache.kafka.common.serialization.StringDeserializer",
                                "value.deserializer",
                                "org.apache.kafka.common.serialization.StringDeserializer",
                                "group.id",
                                "testgroup-" + UUID.randomUUID(),
                                "auto.offset.reset",
                                "earliest"));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    protected static AdminClient getKafkaAdmin() {
        return kafkaContainer.getAdmin();
    }

    @AfterAll
    public static void teardown() {
        if (applicationDeployer != null) {
            // this closes the kubernetes client
            applicationDeployer.close();
        }
    }
}
