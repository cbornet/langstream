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
package ai.langstream.cli.commands.gateway;

import ai.langstream.api.model.Gateway;
import ai.langstream.cli.websocket.WebSocketClient;
import jakarta.websocket.CloseReason;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import lombok.SneakyThrows;
import picocli.CommandLine;

@CommandLine.Command(
        name = "produce",
        mixinStandardHelpOptions = true,
        description = "Produce messages to a gateway")
public class ProduceGatewayCmd extends BaseGatewayCmd {

    record ProduceRequest(Object key, Object value, Map<String, String> headers) {}

    @CommandLine.Parameters(description = "Application ID")
    private String applicationId;

    @CommandLine.Parameters(description = "Gateway ID")
    private String gatewayId;

    @CommandLine.Option(
            names = {"-p", "--param"},
            description = "Gateway parameters. Format: key=value")
    private Map<String, String> params;

    @CommandLine.Option(
            names = {"-c", "--credentials"},
            description =
                    "Credentials for the gateway. Required if the gateway requires authentication.")
    private String credentials;

    @CommandLine.Option(
            names = {"-v", "--value"},
            description = "Message value")
    private String messageValue;

    @CommandLine.Option(
            names = {"-k", "--key"},
            description = "Message key")
    private String messageKey;

    @CommandLine.Option(
            names = {"--header"},
            description = "Messages headers. Format: key=value")
    private Map<String, String> headers;

    @CommandLine.Option(
            names = {"--connect-timeout"},
            description = "Connect timeout for WebSocket connections in seconds.")
    private long connectTimeoutSeconds = 0;

    @Override
    @SneakyThrows
    public void run() {
        final String producePath =
                validateGatewayAndGetUrl(
                        applicationId,
                        gatewayId,
                        Gateway.GatewayType.produce,
                        params,
                        Map.of(),
                        credentials);
        final Duration connectTimeout =
                connectTimeoutSeconds > 0 ? Duration.ofSeconds(connectTimeoutSeconds) : null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try (final WebSocketClient client =
                new WebSocketClient(
                                new WebSocketClient.Handler() {
                                    @Override
                                    public void onMessage(String msg) {
                                        log(msg);
                                        countDownLatch.countDown();
                                    }

                                    @Override
                                    public void onClose(CloseReason closeReason) {
                                        if (closeReason.getCloseCode()
                                                != CloseReason.CloseCodes.NORMAL_CLOSURE) {
                                            err(
                                                    "Server closed connection with unexpected code: %s %s"
                                                            .formatted(
                                                                    closeReason.getCloseCode(),
                                                                    closeReason.getReasonPhrase()));
                                        }
                                        countDownLatch.countDown();
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        err(
                                                "Connection error: %s"
                                                        .formatted(throwable.getMessage()));
                                    }
                                })
                        .connect(URI.create(producePath), connectTimeout)) {
            final ProduceRequest produceRequest =
                    new ProduceRequest(messageKey, messageValue, headers);
            final String json = messageMapper.writeValueAsString(produceRequest);
            client.send(json);
            countDownLatch.await();
        }
    }
}
