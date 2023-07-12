package com.datastax.oss.sga.impl.agents.ai;

import java.util.List;
import java.util.Map;

public class ComputeEmbeddingsAgentProvider extends GenAIToolKitFunctionAgentProvider {

    public ComputeEmbeddingsAgentProvider(String clusterType, String mainAgentType) {
        super("compute-ai-embeddings", clusterType, mainAgentType);
    }

    @Override
    protected void generateSteps(Map<String, Object> originalConfiguration, List<Map<String, Object>> steps) {
        Map<String, Object> step = Map.of(
                "type", "compute-ai-embeddings",
                "model", originalConfiguration.getOrDefault("model", "text-embedding-ada-002"),
                "embeddings-field", originalConfiguration.getOrDefault("embeddings-field", "embeddings"),
                "text", originalConfiguration.getOrDefault("text", "")
        );
        steps.add(step);
    }
}
