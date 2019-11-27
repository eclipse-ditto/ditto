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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.alpakka;

import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Credentials;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.AcceptAnyTrustManager;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import akka.stream.alpakka.mqtt.MqttConnectionSettings;

/**
 * Creates {@link akka.stream.alpakka.mqtt.MqttConnectionSettings} from a given {@link
 * org.eclipse.ditto.model.connectivity.Connection} configuration.
 */
final class MqttConnectionSettingsFactory {

    private static final MqttConnectionSettingsFactory INSTANCE = new MqttConnectionSettingsFactory();
    private static final AcceptAnyTrustManager ACCEPT_ANY_TRUST_MANAGER = new AcceptAnyTrustManager();

    static MqttConnectionSettingsFactory getInstance() {
        return INSTANCE;
    }

    MqttConnectionSettings createMqttConnectionSettings(final Connection connection, final DittoHeaders dittoHeaders) {
        final String uri = connection.getUri();

        final MqttSpecificConfig mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection);
        final String mqttClientId = mqttSpecificConfig.getMqttClientId().orElse(connection.getId().toString());

        MqttConnectionSettings connectionSettings =
                MqttConnectionSettings.create(uri, mqttClientId, new MemoryPersistence());

        connectionSettings = connectionSettings.withAutomaticReconnect(connection.isFailoverEnabled());

        final Optional<String> possibleUsername = connection.getUsername();
        final Optional<String> possiblePassword = connection.getPassword();
        if (possibleUsername.isPresent() && possiblePassword.isPresent()) {
            connectionSettings = connectionSettings.withAuth(possibleUsername.get(), possiblePassword.get());
        }

        if (isSecureConnection(connection)) {
            connectionSettings = applySSLSocketFactory(connection, connectionSettings, dittoHeaders);
        }

        return connectionSettings;
    }

    private MqttConnectionSettings applySSLSocketFactory(final Connection connection,
            final MqttConnectionSettings connectionSettings,
            final DittoHeaders dittoHeaders) {
        final SSLContextCreator sslContextCreator = connection.isValidateCertificates()
                ? SSLContextCreator.fromConnection(connection, dittoHeaders)
                : SSLContextCreator.withTrustManager(ACCEPT_ANY_TRUST_MANAGER, dittoHeaders);

        final Credentials clientCredentials =
                connection.getCredentials().orElseGet(ClientCertificateCredentials::empty);

        final SSLContext sslContext = clientCredentials.accept(sslContextCreator);

        return connectionSettings.withSocketFactory(sslContext.getSocketFactory());
    }

    private boolean isSecureConnection(final Connection connection) {
        return "ssl".equals(connection.getProtocol()) || "wss".equals(connection.getProtocol());
    }
}
