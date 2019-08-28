/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.DittoTrustManagerFactory;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.KeyManagerFactoryFactory;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttClientSslConfigBuilder;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;

/**
 * Default implementation of {@link HiveMqtt3ClientFactory}.
 */
public final class DefaultHiveMqtt3ClientFactory implements HiveMqtt3ClientFactory {

    private static final DefaultHiveMqtt3ClientFactory INSTANCE = new DefaultHiveMqtt3ClientFactory();
    private static final List<String> MQTT_SECURE_SCHEMES = Arrays.asList("ssl", "wss");

    /**
     * @return the singleton instance of {@link DefaultHiveMqtt3ClientFactory}
     */
    public static DefaultHiveMqtt3ClientFactory getInstance() {
        return INSTANCE;
    }

    private DefaultHiveMqtt3ClientFactory() {
    }

    @Override
    public Mqtt3Client newClient(final Connection connection, final String identifier,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener) {
        final Mqtt3ClientBuilder mqtt3ClientBuilder = MqttClient.builder().useMqttVersion3();
        final URI uri = URI.create(connection.getUri());
        mqtt3ClientBuilder.serverHost(uri.getHost());
        mqtt3ClientBuilder.serverPort(uri.getPort());

        // TODO: test if this works -> works!
        final Optional<String> possibleUsername = connection.getUsername();
        final Optional<String> possiblePassword = connection.getPassword();
        if (possibleUsername.isPresent() && possiblePassword.isPresent()) {
            mqtt3ClientBuilder.simpleAuth()
                    .username(possibleUsername.get())
                    .password(possiblePassword.get().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }

        if (connection.isFailoverEnabled()) {
            // TODO: use specific config instead of default config
            mqtt3ClientBuilder.automaticReconnectWithDefaultConfig();
        }

        if (isSecuredConnection(connection.getProtocol())) {

            final MqttClientSslConfigBuilder sslConfigBuilder = MqttClientSslConfig.builder();

            if (connection.isValidateCertificates()) {
                // create DittoTrustManagerFactory to apply hostname verification
                final TrustManagerFactory trustManagerFactory =
                        DittoTrustManagerFactory.from(connection);
                sslConfigBuilder.trustManagerFactory(trustManagerFactory);
            } else {
                // TODO need to configure accept any trust manager?
            }

            connection.getCredentials()
                    .map(credentials -> credentials.accept(KeyManagerFactoryFactory.getInstance()))
                    .ifPresent(sslConfigBuilder::keyManagerFactory);

            mqtt3ClientBuilder.sslConfig(sslConfigBuilder.build());
        }

        if (null != connectedListener) {
            mqtt3ClientBuilder.addConnectedListener(connectedListener);
        }
        if (null != disconnectedListener) {
            mqtt3ClientBuilder.addDisconnectedListener(disconnectedListener);
        }
        return mqtt3ClientBuilder.identifier(identifier).build();
    }

    private static boolean isSecuredConnection(final String protocol) {
        return MQTT_SECURE_SCHEMES.contains(protocol.toLowerCase());
    }
}
