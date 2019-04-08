/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.util.KafkaConfigReader;
import org.eclipse.ditto.services.utils.config.ConfigUtil;

import akka.kafka.ProducerSettings;

/**
 * Creates {@link akka.kafka.ProducerSettings} from a given {@link org.eclipse.ditto.model.connectivity.Connection}
 * configuration.
 */
final class ProducerSettingsFactory {

    private static final Collection<KafkaSpecificConfig> SPECIFIC_CONFIGS =
            Collections.unmodifiableList(Arrays.asList(KafkaAuthenticationSpecificConfig.getInstance(),
                    KafkaBootstrapServerSpecificConfig.getInstance()));

    private static final ProducerSettingsFactory INSTANCE = new ProducerSettingsFactory();
    private static final Serializer<String> KEY_SERIALIZER = new StringSerializer();
    private static final Serializer<String> VALUE_SERIALIZER = KEY_SERIALIZER;

    static ProducerSettingsFactory getInstance() {
        return INSTANCE;
    }

    ProducerSettings<String, String> createProducerSettings(final Connection connection,
            final KafkaConfigReader config) {
        ProducerSettings<String, String> settings =
                ProducerSettings.create(config.internalProducerSettings(), KEY_SERIALIZER, VALUE_SERIALIZER);

        settings = addMetadata(connection, settings);
        settings = addSecurityProtocol(connection, settings);
        settings = addSpecificConfigs(settings, connection);

        return settings;
    }

    private ProducerSettings<String, String> addMetadata(final Connection connection,
            final ProducerSettings<String, String> settings) {
        // identify the connected Kafka client by the connectionId followed by the instance index
        // (in order to be able to differentiate if a clientCount >1 was configured):
        return settings.withProperty(CommonClientConfigs.CLIENT_ID_CONFIG, connection.getId() + "-" +
                ConfigUtil.instanceIdentifier());
    }

    private ProducerSettings<String, String> addSpecificConfigs(final ProducerSettings<String, String> settings,
            final Connection connection) {
        ProducerSettings<String, String> currentSettings = settings;
        for (final KafkaSpecificConfig specificConfig : SPECIFIC_CONFIGS) {
            currentSettings = specificConfig.apply(currentSettings, connection);
        }
        return currentSettings;
    }

    private ProducerSettings<String, String> addSecurityProtocol(final Connection connection,
            final ProducerSettings<String, String> settings) {
        if (isAuthenticatedConnection(connection)) {
            return addAuthenticatedSecurityProtocol(connection, settings);
        }
        return addUnauthenticatedSecurityProtocol(connection, settings);
    }

    private static boolean isAuthenticatedConnection(final Connection connection) {
        return KafkaAuthenticationSpecificConfig.getInstance().isApplicable(connection);
    }

    private static ProducerSettings<String, String> addAuthenticatedSecurityProtocol(final Connection connection,
            final ProducerSettings<String, String> settings) {
        if (isSecureConnection(connection)) {
            return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        }
        return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
    }

    private static ProducerSettings<String, String> addUnauthenticatedSecurityProtocol(final Connection connection,
            final ProducerSettings<String, String> settings) {
        if (isSecureConnection(connection)) {
            return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        }
        return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
    }

    private static boolean isSecureConnection(final Connection connection) {
        return "ssl".equals(connection.getProtocol());
    }

}
