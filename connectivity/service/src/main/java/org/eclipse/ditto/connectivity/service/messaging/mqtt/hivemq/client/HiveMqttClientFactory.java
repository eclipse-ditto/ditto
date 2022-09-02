/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.DittoTrustManagerFactory;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.KeyManagerFactoryFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientBuilder;
import com.hivemq.client.mqtt.MqttClientExecutorConfig;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;

/**
 * Factory for creating HiveMQ MQTT clients for protocol version 3 and 5.
 */
@Immutable
final class HiveMqttClientFactory {

    private static final Set<String> MQTT_SECURE_SCHEMES = Set.of("ssl", "wss");

    private HiveMqttClientFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a {@link Mqtt3Client}.
     *
     * @param hiveMqttClientProperties properties which are required for creating a HiveMQ MQTT client.
     * @param mqttClientIdentifier the identifier of the returned client.
     * @return the Mqtt3Client.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Mqtt3Client getMqtt3Client(final HiveMqttClientProperties hiveMqttClientProperties,
            final MqttClientIdentifier mqttClientIdentifier,
            final ClientRole clientRole) {

        checkArguments(hiveMqttClientProperties, mqttClientIdentifier, clientRole);

        return getGenericMqttClientBuilder(hiveMqttClientProperties, mqttClientIdentifier, clientRole)
                .useMqttVersion3()
                .simpleAuth(getMqtt3SimpleAuth(hiveMqttClientProperties).orElse(null))
                .willPublish(getMqttLastWillMessage(hiveMqttClientProperties)
                        .map(GenericMqttPublish::getAsMqtt3Publish)
                        .orElse(null))
                .build();
    }

    private static void checkArguments(final HiveMqttClientProperties hiveMqttClientProperties,
            final MqttClientIdentifier mqttClientIdentifier,
            final ClientRole clientRole) {

        ConditionChecker.checkNotNull(hiveMqttClientProperties, "hiveMqttClientProperties");
        ConditionChecker.checkNotNull(mqttClientIdentifier, "mqttClientIdentifier");
        ConditionChecker.checkNotNull(clientRole, "clientRole");
    }

    private static Optional<GenericMqttPublish> getMqttLastWillMessage(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final Optional<GenericMqttPublish> result;
        if (hiveMqttClientProperties.isDisableLastWillMessage()) {
            result = Optional.empty();
        } else {
            final var mqttSpecificConfig = hiveMqttClientProperties.getMqttSpecificConfig();
            result = mqttSpecificConfig.getMqttLastWillTopic()
                    .map(lastWillTopic ->
                            GenericMqttPublish.builder(lastWillTopic, mqttSpecificConfig.getLastWillQosOrThrow())
                                    .retain(mqttSpecificConfig.getMqttWillRetain())
                                    .payload(mqttSpecificConfig.getMqttWillMessage()
                                            .map(mqttWillMessage -> mqttWillMessage.getBytes(StandardCharsets.UTF_8))
                                            .map(ByteBuffer::wrap)
                                            .orElse(null))
                                    .build()
                    );
        }
        return result;
    }

    private static MqttClientBuilder getGenericMqttClientBuilder(
            final HiveMqttClientProperties hiveMqttClientProperties,
            final MqttClientIdentifier mqttClientIdentifier,
            final ClientRole clientRole
    ) {
        final var mqttConfig = hiveMqttClientProperties.getMqttConfig();

        return MqttClient.builder()
                .serverAddress(getInetSocketAddress(getConnectionUri(hiveMqttClientProperties)))
                .executorConfig(getMqttClientExecutorConfig(mqttConfig.getEventLoopThreads()))
                .sslConfig(getMqttClientSslConfig(hiveMqttClientProperties).orElse(null))
                .addConnectedListener(getConnectedListener(
                        hiveMqttClientProperties.getMqttClientConnectedListener(),
                        clientRole
                ))
                .addDisconnectedListener(getDisconnectedListener(
                        hiveMqttClientProperties.getMqttClientDisconnectedListener(),
                        clientRole
                ))
                .identifier(mqttClientIdentifier);
    }

    private static URI getConnectionUri(final HiveMqttClientProperties hiveMqttClientProperties) {
        final var sshTunnelState = hiveMqttClientProperties.getSshTunnelState().orElseGet(SshTunnelState::disabled);
        return sshTunnelState.getURI(hiveMqttClientProperties.getMqttConnection());
    }

    private static InetSocketAddress getInetSocketAddress(final URI connectionUri) {
        return new InetSocketAddress(connectionUri.getHost(), connectionUri.getPort());
    }

    private static MqttClientExecutorConfig getMqttClientExecutorConfig(final int eventLoopThreadNumber) {
        var configBuilder = MqttClientExecutorConfig.builder();
        if (0 < eventLoopThreadNumber) {
            configBuilder = configBuilder.nettyThreads(eventLoopThreadNumber);
        }
        return configBuilder.build();
    }

    private static Optional<MqttClientSslConfig> getMqttClientSslConfig(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final Optional<MqttClientSslConfig> result;
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        if (isSecuredConnection(mqttConnection)) {
            result = Optional.of(MqttClientSslConfig.builder()

                    // Create DittoTrustManagerFactory to apply hostname
                    // verification or to disable certificate check when the
                    // connection requires it.
                    .trustManagerFactory(DittoTrustManagerFactory.from(mqttConnection,
                            hiveMqttClientProperties.getConnectionLogger()))
                    .keyManagerFactory(mqttConnection.getCredentials()
                            .map(credentials -> credentials.accept(KeyManagerFactoryFactory.getInstance()))
                            .orElse(null))
                    .build());
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private static boolean isSecuredConnection(final Connection mqttConnection) {
        final var connectionProtocol = mqttConnection.getProtocol();
        return MQTT_SECURE_SCHEMES.contains(connectionProtocol.toLowerCase(Locale.ENGLISH));
    }

    private static MqttClientConnectedListener getConnectedListener(
            final GenericMqttClientConnectedListener genericMqttClientConnectedListener,
            final ClientRole clientRole
    ) {
        return context -> genericMqttClientConnectedListener.onConnected(context, clientRole);
    }

    private static MqttClientDisconnectedListener getDisconnectedListener(
            final GenericMqttClientDisconnectedListener genericMqttClientDisconnectedListener,
            final ClientRole clientRole
    ) {
        return context -> genericMqttClientDisconnectedListener.onDisconnected(context, clientRole);
    }

    private static Optional<Mqtt3SimpleAuth> getMqtt3SimpleAuth(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        return getSimpleAuthCredentials(hiveMqttClientProperties)
                .map(simpleAuthCredentials -> Mqtt3SimpleAuth.builder()
                        .username(simpleAuthCredentials.username())
                        .password(simpleAuthCredentials.passwordBytes())
                        .build());
    }

    private static Optional<SimpleAuthCredentials> getSimpleAuthCredentials(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();

        return mqttConnection.getUsername()
                .flatMap(username -> mqttConnection.getPassword()
                        .map(pw -> pw.getBytes(StandardCharsets.UTF_8))
                        .map(pwBytes -> new SimpleAuthCredentials(username, pwBytes))
                );
    }


    /**
     * Creates a {@link Mqtt5Client}.
     *
     * @param hiveMqttClientProperties properties which are required for creating a HiveMQ MQTT client.
     * @param mqttClientIdentifier the identifier of the returned client.
     * @return the Mqtt3Client.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Mqtt5Client getMqtt5Client(final HiveMqttClientProperties hiveMqttClientProperties,
            final MqttClientIdentifier mqttClientIdentifier,
            final ClientRole clientRole) {

        checkArguments(hiveMqttClientProperties, mqttClientIdentifier, clientRole);

        return getGenericMqttClientBuilder(hiveMqttClientProperties, mqttClientIdentifier, clientRole)
                .useMqttVersion5()
                .simpleAuth(getMqtt5SimpleAuth(hiveMqttClientProperties).orElse(null))
                .willPublish(getMqttLastWillMessage(hiveMqttClientProperties)
                        .map(GenericMqttPublish::getAsMqtt5Publish)
                        .orElse(null))
                .build();
    }

    private static Optional<Mqtt5SimpleAuth> getMqtt5SimpleAuth(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        return getSimpleAuthCredentials(hiveMqttClientProperties)
                .map(connectionCredentials -> Mqtt5SimpleAuth.builder()
                        .username(connectionCredentials.username())
                        .password(connectionCredentials.passwordBytes())
                        .build());
    }

    private record SimpleAuthCredentials(String username, byte[] passwordBytes) {}

}
