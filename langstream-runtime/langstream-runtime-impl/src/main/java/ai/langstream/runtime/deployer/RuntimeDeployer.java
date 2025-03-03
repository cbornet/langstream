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
package ai.langstream.runtime.deployer;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import ai.langstream.api.model.Application;
import ai.langstream.api.model.Secrets;
import ai.langstream.api.runtime.ClusterRuntimeRegistry;
import ai.langstream.api.runtime.ExecutionPlan;
import ai.langstream.api.runtime.PluginsRegistry;
import ai.langstream.impl.deploy.ApplicationDeployer;
import ai.langstream.runtime.api.deployer.RuntimeDeployerConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** This is the main entry point for the deployer runtime. */
@Slf4j
public class RuntimeDeployer {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .configure(
                            FAIL_ON_UNKNOWN_PROPERTIES,
                            false); // this helps with forward compatibility

    public void deploy(
            Map<String, Map<String, Object>> clusterRuntimeConfiguration,
            RuntimeDeployerConfiguration configuration,
            Secrets secrets)
            throws IOException {

        final String applicationId = configuration.getApplicationId();
        log.info(
                "Deploying application {} codeStorageArchiveId {}",
                applicationId,
                configuration.getCodeStorageArchiveId());
        final String applicationConfig = configuration.getApplication();

        final Application appInstance = MAPPER.readValue(applicationConfig, Application.class);
        appInstance.setSecrets(secrets);

        try (ApplicationDeployer deployer =
                ApplicationDeployer.builder()
                        .registry(new ClusterRuntimeRegistry(clusterRuntimeConfiguration))
                        .pluginsRegistry(new PluginsRegistry())
                        .build()) {

            final ExecutionPlan implementation =
                    deployer.createImplementation(applicationId, appInstance);
            deployer.deploy(
                    configuration.getTenant(),
                    implementation,
                    configuration.getCodeStorageArchiveId());
            log.info("Application {} deployed", applicationId);
        }
    }

    public void delete(
            Map<String, Map<String, Object>> clusterRuntimeConfiguration,
            RuntimeDeployerConfiguration configuration,
            Secrets secrets)
            throws IOException {

        final String applicationId = configuration.getApplicationId();
        final String applicationConfig = configuration.getApplication();

        final Application appInstance = MAPPER.readValue(applicationConfig, Application.class);
        appInstance.setSecrets(secrets);

        try (ApplicationDeployer deployer =
                ApplicationDeployer.builder()
                        .registry(new ClusterRuntimeRegistry(clusterRuntimeConfiguration))
                        .pluginsRegistry(new PluginsRegistry())
                        .build()) {

            log.info("Deleting application {}", applicationId);
            final ExecutionPlan implementation =
                    deployer.createImplementation(applicationId, appInstance);
            deployer.delete(
                    configuration.getTenant(),
                    implementation,
                    configuration.getCodeStorageArchiveId());
            log.info("Application {} deleted", applicationId);
        }
    }
}
