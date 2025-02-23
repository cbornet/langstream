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
package ai.langstream.impl.agents.ai;

import ai.langstream.api.model.AgentConfiguration;
import ai.langstream.api.model.Application;
import ai.langstream.api.model.Module;
import ai.langstream.api.model.Pipeline;
import ai.langstream.api.model.Resource;
import ai.langstream.api.runtime.ComponentType;
import ai.langstream.api.runtime.ComputeClusterRuntime;
import ai.langstream.api.runtime.ExecutionPlan;
import ai.langstream.impl.common.AbstractAgentProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenAIToolKitFunctionAgentProvider extends AbstractAgentProvider {

    public static final String AGENT_TYPE = "ai-tools";

    private static final Map<String, StepConfigurationInitializer> STEP_TYPES =
            Map.of(
                    "drop-fields",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "fields");
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "part",
                                            null);
                                }
                            },
                    "merge-key-value", new StepConfigurationInitializer() {},
                    "unwrap-key-value",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "unwrapKey",
                                            null);
                                }
                            },
                    "cast",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "schema-type");
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "part",
                                            null);
                                }
                            },
                    "flatten",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "delimiter",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "part",
                                            null);
                                }
                            },
                    "drop", new StepConfigurationInitializer() {},
                    "compute",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "fields");
                                }
                            },
                    "compute-ai-embeddings",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "model",
                                            "text-embedding-ada-002");
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "embeddings-field");
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "text");
                                }
                            },
                    "query",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {

                                    // reference to datasource
                                    String datasource =
                                            (String) originalConfiguration.remove("datasource");
                                    if (datasource == null) {
                                        // error
                                        requiredField(
                                                step,
                                                agentConfiguration,
                                                originalConfiguration,
                                                "datasource");
                                        return;
                                    }
                                    dataSourceConfigurationGenerator
                                            .generateDataSourceConfiguration(datasource);

                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "fields");
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "query");
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "output-field");
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "only-first",
                                            null);
                                }
                            },
                    "ai-chat-completions",
                            new StepConfigurationInitializer() {
                                @Override
                                public void generateSteps(
                                        Map<String, Object> step,
                                        Map<String, Object> originalConfiguration,
                                        AgentConfiguration agentConfiguration,
                                        DataSourceConfigurationGenerator
                                                dataSourceConfigurationGenerator) {
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "completion-field");
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "log-field",
                                            null);
                                    Object messages =
                                            requiredField(
                                                    step,
                                                    agentConfiguration,
                                                    originalConfiguration,
                                                    "messages");
                                    if (messages instanceof Collection<?> collection) {
                                        for (Object o : collection) {
                                            if (o instanceof Map map) {
                                                map.keySet()
                                                        .forEach(
                                                                k -> {
                                                                    if (!"role".equals(k)
                                                                            && !"content"
                                                                                    .equals(k)) {
                                                                        throw new IllegalArgumentException(
                                                                                "messages must be a list of objects, [{role: 'user', content: 'template'}]");
                                                                    }
                                                                });
                                            } else {
                                                throw new IllegalArgumentException(
                                                        "messages must be a list of objects, [{role: 'user', content: 'template'}]");
                                            }
                                        }
                                    } else {
                                        throw new IllegalArgumentException(
                                                "messages must be a list of objects: [{role: 'user', content: 'template'}]");
                                    }
                                    requiredField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "model");
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "temperature",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "top-p",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "logit-bias",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "stop",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "max-tokens",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "presence-penalty",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "frequency-penalty",
                                            null);
                                    optionalField(
                                            step,
                                            agentConfiguration,
                                            originalConfiguration,
                                            "user",
                                            null);
                                }
                            });

    public GenAIToolKitFunctionAgentProvider(String clusterType) {
        super(STEP_TYPES.keySet(), List.of(clusterType, "none"));
    }

    @Override
    protected final ComponentType getComponentType(AgentConfiguration agentConfiguration) {
        return ComponentType.PROCESSOR;
    }

    private interface StepConfigurationInitializer {
        default void generateSteps(
                Map<String, Object> step,
                Map<String, Object> originalConfiguration,
                AgentConfiguration agentConfiguration,
                DataSourceConfigurationGenerator dataSourceConfigurationGenerator) {}
    }

    protected void generateSteps(
            Map<String, Object> originalConfiguration,
            Map<String, Object> configuration,
            Application application,
            AgentConfiguration agentConfiguration) {
        List<Map<String, Object>> steps = new ArrayList<>();
        configuration.put("steps", steps);
        Map<String, Object> step = new HashMap<>();

        // we are mapping the original name to the ai-tools function name
        step.put("type", agentConfiguration.getType());

        // on every step you can put a "when" clause
        optionalField(step, agentConfiguration, originalConfiguration, "when", null);

        DataSourceConfigurationGenerator dataSourceConfigurationInjector =
                (resourceId) ->
                        generateDataSourceConfiguration(resourceId, application, configuration);

        STEP_TYPES
                .get(agentConfiguration.getType())
                .generateSteps(
                        step,
                        originalConfiguration,
                        agentConfiguration,
                        dataSourceConfigurationInjector);
        steps.add(step);
    }

    interface DataSourceConfigurationGenerator {
        void generateDataSourceConfiguration(String resourceId);
    }

    private void generateAIProvidersConfiguration(
            Application applicationInstance, Map<String, Object> configuration) {
        // let the user force the provider or detect it automatically
        String service = (String) configuration.remove("service");
        for (Resource resource : applicationInstance.getResources().values()) {
            HashMap<String, Object> configurationCopy = new HashMap<>(resource.configuration());
            switch (resource.type()) {
                case "vertex-configuration":
                    if (service == null || service.equals("vertex")) {
                        configuration.put("vertex", configurationCopy);
                    }
                    configuration.put("vertex", configurationCopy);
                    break;
                case "hugging-face-configuration":
                    if (service == null || service.equals("hugging-face")) {
                        configuration.put("huggingface", configurationCopy);
                    }
                    break;
                case "open-ai-configuration":
                    if (service == null || service.equals("open-ai")) {
                        configuration.put("openai", configurationCopy);
                    }
                    break;
                default:
                    // ignore
            }
        }
    }

    private void generateDataSourceConfiguration(
            String resourceId, Application applicationInstance, Map<String, Object> configuration) {
        Resource resource = applicationInstance.getResources().get(resourceId);
        log.info("Generating datasource configuration for {}", resourceId);
        if (resource != null) {
            if (!resource.type().equals("datasource")) {
                throw new IllegalArgumentException(
                        "Resource " + resourceId + " is not type=datasource");
            }
            if (configuration.containsKey("datasource")) {
                throw new IllegalArgumentException("Only one datasource is supported");
            }
            configuration.put("datasource", new HashMap<>(resource.configuration()));
        } else {
            throw new IllegalArgumentException("Resource " + resourceId + " not found");
        }
    }

    @Override
    protected Map<String, Object> computeAgentConfiguration(
            AgentConfiguration agentConfiguration,
            Module module,
            Pipeline pipeline,
            ExecutionPlan executionPlan,
            ComputeClusterRuntime clusterRuntime) {
        Map<String, Object> originalConfiguration =
                super.computeAgentConfiguration(
                        agentConfiguration, module, pipeline, executionPlan, clusterRuntime);
        Map<String, Object> configuration = new HashMap<>();

        generateAIProvidersConfiguration(executionPlan.getApplication(), configuration);

        generateSteps(
                originalConfiguration,
                configuration,
                executionPlan.getApplication(),
                agentConfiguration);
        return configuration;
    }

    protected static <T> T requiredField(
            Map<String, Object> step,
            AgentConfiguration agentConfiguration,
            Map<String, Object> originalConfiguration,
            String name) {
        if (!originalConfiguration.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Missing required field '"
                            + name
                            + "' in agent definition, type="
                            + agentConfiguration.getType()
                            + ", name="
                            + agentConfiguration.getName()
                            + ", id="
                            + agentConfiguration.getId());
        }
        Object value = originalConfiguration.get(name);
        step.put(name, value);
        return (T) value;
    }

    protected static void optionalField(
            Map<String, Object> step,
            AgentConfiguration agentConfiguration,
            Map<String, Object> originalConfiguration,
            String name,
            Object defaultValue) {
        if (!originalConfiguration.containsKey(name)) {
            if (defaultValue != null) {
                step.put(name, defaultValue);
            }
        } else {
            step.put(name, originalConfiguration.get(name));
        }
    }
}
