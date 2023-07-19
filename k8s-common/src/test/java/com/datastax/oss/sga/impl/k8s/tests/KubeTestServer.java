package com.datastax.oss.sga.impl.k8s.tests;

import com.datastax.oss.sga.deployer.k8s.api.crds.agents.AgentCustomResource;
import com.datastax.oss.sga.impl.k8s.KubernetesClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@Slf4j
public class KubeTestServer implements AutoCloseable, BeforeEachCallback, BeforeAllCallback, AfterAllCallback {

    public static class Server extends KubernetesMockServer {

        @Override
        @SneakyThrows
        public void init() {
            super.init();
            KubernetesClientFactory.set(null, createClient());
        }

        @Override
        public void destroy() {
            super.destroy();
            KubernetesClientFactory.clear();
        }
    }

    private final Server server = new Server();

    @SneakyThrows
    public void start() {
        server.init();
    }

    @Override
    public void close() throws Exception {
        server.destroy();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        server.reset();
        currentAgents.clear();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        close();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        start();
    }


    private Map<String, AgentCustomResource> currentAgents = new HashMap<>();

    public Map<String, AgentCustomResource> spyAgentCustomResources(final String namespace,
                                                                    String... expectedAgents) {
        for (String agentId : expectedAgents) {
            final String fullPath =
                    "/apis/sga.oss.datastax.com/v1alpha1/namespaces/%s/agents/%s".formatted(
                            namespace, agentId);
            server.expect()
                    .patch()
                    .withPath(fullPath + "?fieldManager=fabric8")
                    .andReply(
                            HttpURLConnection.HTTP_OK,
                            recordedRequest -> {
                                try {
                                    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                    recordedRequest.getBody().copyTo(byteArrayOutputStream);
                                    final ObjectMapper mapper = new ObjectMapper()
                                            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
                                    final AgentCustomResource agent =
                                            mapper.readValue(byteArrayOutputStream.toByteArray(),
                                                    AgentCustomResource.class);
                                    log.info("received patch request for agent {}", agentId);
                                    currentAgents.put(agentId, agent);
                                    return agent;
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    ).always();

            server.expect()
                    .delete()
                    .withPath(fullPath)
                    .andReply(
                            HttpURLConnection.HTTP_OK,
                            recordedRequest -> {
                                log.info("received delete request for agent {}", agentId);
                                currentAgents.remove(agentId);
                                return List.of();
                            }
                    ).always();
        }
        return currentAgents;
    }

}
