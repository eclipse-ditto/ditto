/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.util.KafkaConfigReader;

import akka.kafka.ProducerSettings;

/**
 * Creates {@link akka.kafka.ProducerSettings} from a given {@link
 * org.eclipse.ditto.model.connectivity.Connection} configuration.
 */
final class ProducerSettingsFactory {

    private static final ProducerSettingsFactory INSTANCE = new ProducerSettingsFactory();
    // TODO: do we need an own serializer?
    private static final Serializer<String> KEY_SERIALIZER = new StringSerializer();
    private static final Serializer<String> VALUE_SERIALIZER = KEY_SERIALIZER;

    static ProducerSettingsFactory getInstance() {
        return INSTANCE;
    }

    ProducerSettings<String, String> createProducerSettings(final Connection connection, final DittoHeaders dittoHeaders, final KafkaConfigReader config) {
        // TODO: config may not be null!
        ProducerSettings settings = ProducerSettings.create(config.internalProducerSettings(), KEY_SERIALIZER, VALUE_SERIALIZER)
                .withBootstrapServers(getBootstrapServers(connection))
                .withProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, "10000"); // TODO blocking timeout, either due to missing metadata or due to full buffer, reset to 60.000
//                .withCloseTimeout()
//                .withDispatcher()
//                .withEosCommitInterval()
//                .withParallelism()

        final Optional<String> possibleUsername = connection.getUsername();
        final Optional<String> possiblePassword = connection.getPassword();
        if (possibleUsername.isPresent() && possiblePassword.isPresent()) {
            settings = addAuthentication(possibleUsername.get(), possiblePassword.get(), settings);
        }

        if (isSecureConnection(connection)) {
            settings = applySSLSocketFactory(connection, settings, dittoHeaders);
        }

        return settings;
    }

    private ProducerSettings<String, String> addAuthentication(final String username,
            final String password,
            final ProducerSettings<String, String> producerSettings) {

        // TODO: if SSL is enabled, use SASL_SSL, otherwise SASL_PLAINTEXT
        return producerSettings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
                .withProperty(SaslConfigs.SASL_MECHANISM, "PLAIN")
                .withProperty(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"" + username +"\" " +
                "password=\"" + password +"\";");
    }

    private ProducerSettings<String, String> applySSLSocketFactory(final Connection connection,
            final ProducerSettings<String, String> producerSettings,
            final DittoHeaders dittoHeaders) {

        ProducerSettings<String, String> settingsWithSSL = producerSettings;
        // TODO: implement
        /**
         * client authentication not required
         * ssl.truststore.location=/var/private/ssl/client.truststore.jks
         * ssl.truststore.password=test1234
         */

        /**
         * client authentication required
         * ssl.keystore.location=/var/private/ssl/client.keystore.jks
         * ssl.keystore.password=test1234
         * ssl.key.password=test1234
         */

        return settingsWithSSL;
    }

    private static boolean isSecureConnection(final Connection connection) {
        return "ssl".equals(connection.getProtocol());
    }

    private static String getBootstrapServers(final Connection connection) {
        // TODO: add validation for bootstrapServers to validator
        final String additionalBootstrapServers = connection.getSpecificConfig().get("bootstrapServers");
        if (null != additionalBootstrapServers && !additionalBootstrapServers.isEmpty()) {
            final String serverWithoutProtocol = connection.getHostname() + ":" + connection.getPort();
            return mergeAdditionalBootstrapServers(serverWithoutProtocol, additionalBootstrapServers);
        }
        return connection.getUri();
    }

    private static String mergeAdditionalBootstrapServers(final String serverWithoutProtocol, final String additionalBootstrapServers) {
        final Set<String> additionalServers = Arrays.stream(additionalBootstrapServers.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        additionalServers.add(serverWithoutProtocol);
        return String.join(",", additionalServers);
    }
}
