/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.DittoTrustManagerFactory;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.KeyManagerFactoryFactory;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;

import com.hivemq.client.mqtt.MqttClientBuilderBase;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttClientSslConfigBuilder;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuthBuilder;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3WillPublishBuilder;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuthBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5WillPublishBuilder;

/**
 * Common code between MQTT3 and MQTT5 client factories.
 */
abstract class AbstractHiveMqttClientFactory {

    private static final List<String> MQTT_SECURE_SCHEMES = Arrays.asList("ssl", "wss");
    private final Supplier<SshTunnelState> tunnelStateSupplier;

    protected AbstractHiveMqttClientFactory(final Supplier<SshTunnelState> tunnelStateSupplier) {
        this.tunnelStateSupplier = tunnelStateSupplier;
    }

    // duplicate code unavoidable because there is no common interface
    // between Mqtt3SimpleAuthBuilder.Nested and Mqtt5SimpleAuthBuilder.Nested
    @SuppressWarnings("Duplicates")
    void configureSimpleAuth(final Mqtt3SimpleAuthBuilder.Nested<?> simpleAuth, final Connection connection,
            final boolean doubleDecodingEnabled) {

        final Optional<String> username = connection.getUsername(doubleDecodingEnabled);
        final Optional<String> password = connection.getPassword(doubleDecodingEnabled);
        if (username.isPresent() && password.isPresent()) {
            simpleAuth.username(username.get())
                    .password(password.get().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }
    }

    // duplicate code unavoidable because there is no common interface
    // between Mqtt3SimpleAuthBuilder.Nested and Mqtt5SimpleAuthBuilder.Nested
    @SuppressWarnings("Duplicates")
    void configureSimpleAuth(final Mqtt5SimpleAuthBuilder.Nested<?> simpleAuth, final Connection connection,
            final boolean doubleDecodingEnabled) {

        final Optional<String> username = connection.getUsername(doubleDecodingEnabled);
        final Optional<String> password = connection.getPassword(doubleDecodingEnabled);
        if (username.isPresent() && password.isPresent()) {
            simpleAuth.username(username.get())
                    .password(password.get().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }
    }

    @SuppressWarnings("Duplicates")
    void configureWillPublish(final Mqtt3ClientBuilder clientBuilder, final MqttSpecificConfig mqttSpecificConfig) {
        // since topic is required, the other last will parameters will only be applied if the topic is set
        mqttSpecificConfig.getMqttWillTopic()
                .map(topic -> clientBuilder.willPublish().topic(topic))
                .map(step -> step.retain(mqttSpecificConfig.getMqttWillRetain()))
                .map(step -> Optional.ofNullable(MqttQos.fromCode(mqttSpecificConfig.getMqttWillQos()))
                        .map(step::qos)
                        .orElse(step))
                .map(step -> mqttSpecificConfig.getMqttWillMessage()
                        .map(msg -> step.payload(msg.getBytes(StandardCharsets.UTF_8)))
                        .orElse(step))
                .ifPresent(Mqtt3WillPublishBuilder.Nested.Complete::applyWillPublish);
    }

    @SuppressWarnings("Duplicates")
    void configureWillPublish(final Mqtt5ClientBuilder clientBuilder, final MqttSpecificConfig mqttSpecificConfig) {
        // since topic is required, the other last will parameters will only be applied if the topic is set
        mqttSpecificConfig.getMqttWillTopic()
                .map(topic -> clientBuilder.willPublish().topic(topic))
                .map(step -> step.retain(mqttSpecificConfig.getMqttWillRetain()))
                .map(step -> Optional.ofNullable(MqttQos.fromCode(mqttSpecificConfig.getMqttWillQos()))
                        .map(step::qos)
                        .orElse(step))
                .map(step -> mqttSpecificConfig.getMqttWillMessage()
                        .map(msg -> step.payload(msg.getBytes(StandardCharsets.UTF_8)))
                        .orElse(step))
                .ifPresent(Mqtt5WillPublishBuilder.Nested.Complete::applyWillPublish);
    }

    <T extends MqttClientBuilderBase<T>> T configureClientBuilder(
            final T newBuilder,
            final Connection connection,
            final String identifier,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener,
            final ConnectionLogger connectionLogger,
            final int eventLoopThreads) {

        final var uri = tunnelStateSupplier.get().getURI(connection);

        T builder = newBuilder;
        if (eventLoopThreads > 0) {
            builder = builder.executorConfig()
                    .nettyThreads(eventLoopThreads)
                    .applyExecutorConfig();
        }

        builder = builder
                .transportConfig()
                .applyTransportConfig()
                .serverHost(uri.getHost())
                .serverPort(uri.getPort());

        if (isSecuredConnection(connection.getProtocol())) {
            // create DittoTrustManagerFactory to apply hostname verification
            // or to disable certificate check when the connection requires it
            MqttClientSslConfigBuilder sslConfigBuilder = MqttClientSslConfig.builder()
                    .trustManagerFactory(DittoTrustManagerFactory.from(connection, connectionLogger));

            final Optional<KeyManagerFactory> keyManagerFactory = connection.getCredentials()
                    .map(credentials -> credentials.accept(KeyManagerFactoryFactory.getInstance()));

            if (keyManagerFactory.isPresent()) {
                sslConfigBuilder = sslConfigBuilder.keyManagerFactory(keyManagerFactory.get());
            }

            builder = builder.sslConfig(sslConfigBuilder.build());
        }

        if (null != connectedListener) {
            builder = builder.addConnectedListener(connectedListener);
        }
        if (null != disconnectedListener) {
            builder = builder.addDisconnectedListener(disconnectedListener);
        }
        builder = builder.identifier(identifier);

        return builder;
    }

    private static boolean isSecuredConnection(final String protocol) {
        return MQTT_SECURE_SCHEMES.contains(protocol.toLowerCase());
    }

}
