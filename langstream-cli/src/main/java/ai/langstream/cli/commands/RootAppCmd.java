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
package ai.langstream.cli.commands;

import ai.langstream.cli.commands.applications.AbstractDeployApplicationCmd;
import ai.langstream.cli.commands.applications.DeleteApplicationCmd;
import ai.langstream.cli.commands.applications.DownloadApplicationCodeCmd;
import ai.langstream.cli.commands.applications.GetApplicationCmd;
import ai.langstream.cli.commands.applications.GetApplicationLogsCmd;
import ai.langstream.cli.commands.applications.ListApplicationCmd;
import lombok.Getter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "apps",
        mixinStandardHelpOptions = true,
        description = "Manage LangStream applications",
        subcommands = {
            AbstractDeployApplicationCmd.DeployApplicationCmd.class,
            AbstractDeployApplicationCmd.UpdateApplicationCmd.class,
            ListApplicationCmd.class,
            DeleteApplicationCmd.class,
            GetApplicationCmd.class,
            GetApplicationLogsCmd.class,
            DownloadApplicationCodeCmd.class
        })
@Getter
public class RootAppCmd {
    @CommandLine.ParentCommand private RootCmd rootCmd;
}
