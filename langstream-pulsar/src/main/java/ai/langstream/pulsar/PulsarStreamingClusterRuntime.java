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
package ai.langstream.pulsar;

import static ai.langstream.pulsar.PulsarClientUtils.buildPulsarAdmin;
import static ai.langstream.pulsar.PulsarClientUtils.getPulsarClusterRuntimeConfiguration;

import ai.langstream.api.model.Application;
import ai.langstream.api.model.SchemaDefinition;
import ai.langstream.api.model.TopicDefinition;
import ai.langstream.api.runtime.AgentNode;
import ai.langstream.api.runtime.ConnectionImplementation;
import ai.langstream.api.runtime.ExecutionPlan;
import ai.langstream.api.runtime.StreamingClusterRuntime;
import ai.langstream.api.runtime.Topic;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.impl.schema.KeyValueSchemaInfo;
import org.apache.pulsar.common.naming.TopicName;
import org.apache.pulsar.common.schema.KeyValueEncodingType;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaType;

@Slf4j
public class PulsarStreamingClusterRuntime implements StreamingClusterRuntime {

    @Override
    @SneakyThrows
    public void deploy(ExecutionPlan applicationInstance) {
        Application logicalInstance = applicationInstance.getApplication();
        try (PulsarAdmin admin =
                buildPulsarAdmin(logicalInstance.getInstance().streamingCluster())) {
            for (Topic topic : applicationInstance.getLogicalTopics()) {
                deployTopic(admin, (PulsarTopic) topic);
            }
        }
    }

    private static void deployTopic(PulsarAdmin admin, PulsarTopic topic)
            throws PulsarAdminException {
        String createMode = topic.createMode();
        String namespace = topic.name().tenant() + "/" + topic.name().namespace();
        String topicName =
                topic.name().tenant() + "/" + topic.name().namespace() + "/" + topic.name().name();
        log.info("Listing topics in namespace {}", namespace);
        List<String> existing;
        if (topic.partitions() <= 0) {
            existing = admin.topics().getList(namespace);
        } else {
            existing = admin.topics().getPartitionedTopicList(namespace);
        }
        log.info("Existing topics: {}", existing);
        String fullyQualifiedName = TopicName.get(topicName).toString();
        log.info("Looking for : {}", fullyQualifiedName);
        boolean exists = existing.contains(fullyQualifiedName);
        if (exists) {
            log.info("Topic {} already exists", topicName);
        } else {
            log.info("Topic {} does not exist", topicName);
        }
        switch (createMode) {
            case TopicDefinition.CREATE_MODE_CREATE_IF_NOT_EXISTS -> {
                if (!exists) {
                    log.info("Topic {} does not exist, creating", topicName);
                    if (topic.partitions() <= 0) {
                        admin.topics().createNonPartitionedTopic(topicName);
                    } else {
                        admin.topics().createPartitionedTopic(topicName, topic.partitions());
                    }
                }
            }
            case TopicDefinition.CREATE_MODE_NONE -> {
                // do nothing
            }
            default -> throw new IllegalArgumentException("Unknown create mode " + createMode);
        }

        // deploy schema
        if (topic.valueSchema() != null) {
            List<SchemaInfo> allSchemas = admin.schemas().getAllSchemas(topicName);
            if (allSchemas.isEmpty()) {
                log.info("Deploying schema for topic {}: {}", topicName, topic.valueSchema());

                SchemaInfo schemaInfo = getSchemaInfo(topic.valueSchema());
                log.info("Value schema {}", schemaInfo);
                if (topic.keySchema() != null) {
                    // KEY VALUE
                    log.info("Deploying key schema for topic {}: {}", topicName, topic.keySchema());
                    SchemaInfo keySchemaInfo = getSchemaInfo(topic.keySchema());
                    log.info("Key schema {}", keySchemaInfo);

                    schemaInfo =
                            KeyValueSchemaInfo.encodeKeyValueSchemaInfo(
                                    topic.valueSchema().name(),
                                    keySchemaInfo,
                                    schemaInfo,
                                    KeyValueEncodingType.SEPARATED);

                    log.info("KeyValue schema {}", schemaInfo);
                }

                admin.schemas().createSchema(topicName, schemaInfo);
            } else {
                log.info(
                        "Topic {} already has some schemas, skipping. ({})", topicName, allSchemas);
            }
        }
    }

    private static SchemaInfo getSchemaInfo(SchemaDefinition logicalSchemaDefinition) {
        SchemaType pulsarSchemaType =
                SchemaType.valueOf(logicalSchemaDefinition.type().toUpperCase());
        return SchemaInfo.builder()
                .type(pulsarSchemaType)
                .name(logicalSchemaDefinition.name())
                .properties(Map.of())
                .schema(
                        logicalSchemaDefinition.schema() != null
                                ? logicalSchemaDefinition.schema().getBytes(StandardCharsets.UTF_8)
                                : new byte[0])
                .build();
    }

    private static void deleteTopic(PulsarAdmin admin, PulsarTopic topic)
            throws PulsarAdminException {

        switch (topic.createMode()) {
            case TopicDefinition.CREATE_MODE_CREATE_IF_NOT_EXISTS -> {}
            default -> {
                log.info("Keeping Pulsar topic {}", topic.name());
                return;
            }
        }

        String topicName =
                topic.name().tenant() + "/" + topic.name().namespace() + "/" + topic.name().name();
        String fullyQualifiedName = TopicName.get(topicName).toString();
        log.info("Deleting topic {}", fullyQualifiedName);
        try {
            if (topic.partitions() <= 0) {
                admin.topics().delete(fullyQualifiedName, true);
            } else {
                admin.topics().deletePartitionedTopic(fullyQualifiedName, true);
            }
        } catch (PulsarAdminException.NotFoundException notFoundException) {
            log.info("Topic {} didn't exit. Not a problem", fullyQualifiedName);
        }
    }

    @Override
    @SneakyThrows
    public void delete(ExecutionPlan applicationInstance) {
        Application logicalInstance = applicationInstance.getApplication();
        try (PulsarAdmin admin =
                buildPulsarAdmin(logicalInstance.getInstance().streamingCluster())) {
            for (Topic topic : applicationInstance.getLogicalTopics()) {
                deleteTopic(admin, (PulsarTopic) topic);
            }
        }
    }

    @Override
    public Topic createTopicImplementation(
            TopicDefinition topicDefinition, ExecutionPlan applicationInstance) {
        final PulsarClusterRuntimeConfiguration config =
                getPulsarClusterRuntimeConfiguration(
                        applicationInstance.getApplication().getInstance().streamingCluster());

        SchemaDefinition keySchema = topicDefinition.getKeySchema();
        SchemaDefinition valueSchema = topicDefinition.getValueSchema();
        String name = topicDefinition.getName();
        String tenant = config.getDefaultTenant();
        String creationMode = topicDefinition.getCreationMode();
        String namespace = config.getDefaultNamespace();
        PulsarName topicName = new PulsarName(tenant, namespace, name);
        return new PulsarTopic(
                topicName,
                topicDefinition.getPartitions(),
                keySchema,
                valueSchema,
                creationMode,
                topicDefinition.isImplicit());
    }

    @Override
    public Map<String, Object> createConsumerConfiguration(
            AgentNode agentImplementation, ConnectionImplementation inputConnectionImplementation) {
        PulsarTopic pulsarTopic = (PulsarTopic) inputConnectionImplementation;
        Map<String, Object> configuration = new HashMap<>();
        configuration.computeIfAbsent(
                "subscriptionName", key -> "langstream-agent-" + agentImplementation.getId());

        configuration.put("topic", pulsarTopic.name().toPulsarName());
        return configuration;
    }

    @Override
    public Map<String, Object> createProducerConfiguration(
            AgentNode agentImplementation,
            ConnectionImplementation outputConnectionImplementation) {
        PulsarTopic pulsarTopic = (PulsarTopic) outputConnectionImplementation;

        Map<String, Object> configuration = new HashMap<>();
        // TODO: handle other configurations and schema

        configuration.put("topic", pulsarTopic.name().toPulsarName());
        return configuration;
    }
}
