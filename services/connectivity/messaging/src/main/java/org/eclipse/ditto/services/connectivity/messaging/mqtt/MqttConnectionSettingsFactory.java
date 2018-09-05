/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import akka.stream.alpakka.mqtt.MqttConnectionSettings;

/**
 * Creates {@link akka.stream.alpakka.mqtt.MqttConnectionSettings} from a given {@link
 * org.eclipse.ditto.model.connectivity.Connection} configuration.
 */
class MqttConnectionSettingsFactory {

    private static final MqttConnectionSettingsFactory INSTANCE = new MqttConnectionSettingsFactory();
    private static final AcceptAnyTrustManager ACCEPT_ANY_TRUST_MANAGER = new AcceptAnyTrustManager();
    private static final String TLS12 = "TLSv1.2";

    static MqttConnectionSettingsFactory getInstance() {
        return INSTANCE;
    }

    MqttConnectionSettings createMqttConnectionSettings(final Connection connection) {
        final String uri = connection.getUri();
        MqttConnectionSettings connectionSettings = MqttConnectionSettings
                .create(uri, connection.getId(), new MemoryPersistence());

        connectionSettings = connectionSettings.withAutomaticReconnect(connection.isFailoverEnabled());

        final Optional<String> possibleUsername = connection.getUsername();
        final Optional<String> possiblePassword = connection.getPassword();
        if (possibleUsername.isPresent() && possiblePassword.isPresent()) {
            connectionSettings = connectionSettings.withAuth(possibleUsername.get(), possiblePassword.get());
        }

        if (isSecureconnection(connection)) {
            connectionSettings = applySSLSocketFactory(connection, connectionSettings);
        }

        return connectionSettings;
    }

    private MqttConnectionSettings applySSLSocketFactory(final Connection connection,
            final MqttConnectionSettings connectionSettings) {
        try {
            if (connection.isValidateCertificates()) {
                return SocketFactoryExtension.withSocketFactory(connectionSettings,
                        SSLContext.getDefault().getSocketFactory());
            } else {
                final SSLContext sslContext = SSLContext.getInstance(TLS12);
                sslContext.init(null, new TrustManager[]{ACCEPT_ANY_TRUST_MANAGER}, null);
                return SocketFactoryExtension.withSocketFactory(connectionSettings, sslContext.getSocketFactory());
            }
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw ConnectionFailedException.newBuilder(connection.getId())
                    .description("Failed to create SSL context: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private boolean isSecureconnection(final Connection connection) {
        return "ssl".equals(connection.getProtocol()) || "wss".equals(connection.getProtocol());
    }
}