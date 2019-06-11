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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.config.KafkaConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;

import akka.kafka.ProducerSettings;

/**
 * Creates {@link akka.kafka.ProducerSettings} from a given {@link org.eclipse.ditto.model.connectivity.Connection}
 * configuration.
 */
final class ProducerSettingsFactory {

    private static final Collection<KafkaSpecificConfig> SPECIFIC_CONFIGS =
            Collections.unmodifiableList(Arrays.asList(KafkaAuthenticationSpecificConfig.getInstance(),
                    KafkaBootstrapServerSpecificConfig.getInstance()));

    private static final Serializer<String> KEY_SERIALIZER = new StringSerializer();
    private static final Serializer<String> VALUE_SERIALIZER = KEY_SERIALIZER;

    private final Connection connection;
    private final KafkaConfig kafkaConfig;

    private ProducerSettingsFactory(final Connection connection, final KafkaConfig kafkaConfig) {
        this.connection = checkNotNull(connection, "connection");
        this.kafkaConfig = checkNotNull(kafkaConfig, "Kafka config");
    }

    /**
     * Returns an instance of the ProducerSettings factory.
     *
     * @param connection the Kafka connection.
     * @param kafkaConfig the Kafka configuration settings.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static ProducerSettingsFactory getInstance(final Connection connection, final KafkaConfig kafkaConfig) {
        return new ProducerSettingsFactory(connection, kafkaConfig);
    }

    ProducerSettings<String, String> getProducerSettings() {
        ProducerSettings<String, String> settings =
                ProducerSettings.create(kafkaConfig.getInternalProducerConfig(), KEY_SERIALIZER, VALUE_SERIALIZER);

        settings = addMetadata(settings);
        settings = addSecurityProtocol(settings);
        settings = addSpecificConfigs(settings);

        return settings;
    }

    private ProducerSettings<String, String> addMetadata(final ProducerSettings<String, String> settings) {
        // identify the connected Kafka client by the connectionId followed by the instance index
        // (in order to be able to differentiate if a clientCount >1 was configured):
        final InstanceIdentifierSupplier instanceIdentifierSupplier = InstanceIdentifierSupplier.getInstance();

        return settings.withProperty(CommonClientConfigs.CLIENT_ID_CONFIG,
                connection.getId() + "-" + instanceIdentifierSupplier.get());
    }

    private ProducerSettings<String, String> addSpecificConfigs(final ProducerSettings<String, String> settings) {
        ProducerSettings<String, String> currentSettings = settings;
        for (final KafkaSpecificConfig specificConfig : SPECIFIC_CONFIGS) {
            currentSettings = specificConfig.apply(currentSettings, connection);
        }
        return currentSettings;
    }

    private ProducerSettings<String, String> addSecurityProtocol(final ProducerSettings<String, String> settings) {
        if (isConnectionAuthenticated()) {
            return addAuthenticatedSecurityProtocol(settings);
        }
        return addUnauthenticatedSecurityProtocol(settings);
    }

    private boolean isConnectionAuthenticated() {
        final KafkaSpecificConfig authenticationSpecificConfig = KafkaAuthenticationSpecificConfig.getInstance();
        return authenticationSpecificConfig.isApplicable(connection);
    }

    private ProducerSettings<String, String> addAuthenticatedSecurityProtocol(
            final ProducerSettings<String, String> settings) {

        if (isConnectionSecure()) {
            return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        }
        return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
    }

    private ProducerSettings<String, String> addUnauthenticatedSecurityProtocol(
            final ProducerSettings<String, String> settings) {

        if (isConnectionSecure()) {
            return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        }
        return settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
    }

    private boolean isConnectionSecure() {
        return "ssl".equals(connection.getProtocol());
    }

}
