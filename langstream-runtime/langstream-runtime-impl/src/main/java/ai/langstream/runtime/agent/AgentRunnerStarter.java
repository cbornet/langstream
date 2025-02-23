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
package ai.langstream.runtime.agent;

import static ai.langstream.runtime.api.agent.AgentRunnerConstants.AGENTS_ENV;
import static ai.langstream.runtime.api.agent.AgentRunnerConstants.AGENTS_ENV_DEFAULT;
import static ai.langstream.runtime.api.agent.AgentRunnerConstants.DOWNLOADED_CODE_PATH_ENV;
import static ai.langstream.runtime.api.agent.AgentRunnerConstants.DOWNLOADED_CODE_PATH_ENV_DEFAULT;
import static ai.langstream.runtime.api.agent.AgentRunnerConstants.POD_CONFIG_ENV;
import static ai.langstream.runtime.api.agent.AgentRunnerConstants.POD_CONFIG_ENV_DEFAULT;

import ai.langstream.runtime.RuntimeStarter;
import ai.langstream.runtime.agent.api.AgentInfo;
import ai.langstream.runtime.api.agent.RuntimePodConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** This is the main entry point for the pods that run the LangStream runtime and Java code. */
@Slf4j
public class AgentRunnerStarter extends RuntimeStarter {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private static final MainErrorHandler mainErrorHandler =
            error -> {
                log.error("Unexpected error", error);
                System.exit(-1);
            };

    public interface MainErrorHandler {
        void handleError(Throwable error);
    }

    @SneakyThrows
    public static void main(String... args) {
        try {
            new AgentRunnerStarter(new AgentRunner()).start(args);
        } catch (Throwable error) {
            log.info("Error, NOW SLEEPING", error);
            Thread.sleep(60000);
            mainErrorHandler.handleError(error);
        }
    }

    private final AgentRunner agentRunner;

    public AgentRunnerStarter(AgentRunner agentRunner) {
        this.agentRunner = agentRunner;
    }

    public void start(String... args) throws Exception {

        // backward compatibility: <pod configuration file> <code directory> <agents directory>
        Path podRuntimeConfiguration;
        Path codeDirectory;
        Path agentsDirectory;
        if (args.length == 0) {
            podRuntimeConfiguration = getPathFromEnv(POD_CONFIG_ENV, POD_CONFIG_ENV_DEFAULT);
            codeDirectory =
                    getPathFromEnv(DOWNLOADED_CODE_PATH_ENV, DOWNLOADED_CODE_PATH_ENV_DEFAULT);
            agentsDirectory = getPathFromEnv(AGENTS_ENV, AGENTS_ENV_DEFAULT);
        } else if (args.length == 1) {
            podRuntimeConfiguration = Path.of(args[0]);
            codeDirectory =
                    getPathFromEnv(DOWNLOADED_CODE_PATH_ENV, DOWNLOADED_CODE_PATH_ENV_DEFAULT);
            agentsDirectory = getPathFromEnv(AGENTS_ENV, AGENTS_ENV_DEFAULT);
        } else if (args.length == 2) {
            podRuntimeConfiguration = Path.of(args[0]);
            codeDirectory = Path.of(args[1]);
            agentsDirectory = getPathFromEnv(AGENTS_ENV, AGENTS_ENV_DEFAULT);
        } else if (args.length == 3) {
            podRuntimeConfiguration = Path.of(args[0]);
            codeDirectory = Path.of(args[1]);
            agentsDirectory = Path.of(args[2]);
        } else {
            throw new IllegalArgumentException("Invalid arguments: " + String.join(", ", args));
        }

        log.info("Loading pod configuration from {}", podRuntimeConfiguration);
        RuntimePodConfiguration configuration =
                MAPPER.readValue(podRuntimeConfiguration.toFile(), RuntimePodConfiguration.class);

        log.info("Loading code from {}", codeDirectory);
        log.info("Loading agents from {}", agentsDirectory);

        agentRunner.run(
                configuration,
                podRuntimeConfiguration,
                codeDirectory,
                agentsDirectory,
                new AgentInfo(),
                -1);
    }
}
