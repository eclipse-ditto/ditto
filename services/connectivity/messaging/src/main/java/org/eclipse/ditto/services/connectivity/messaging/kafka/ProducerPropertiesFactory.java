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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.config.KafkaConfig;

import com.typesafe.config.Config;

/**
 * Creates Kafka producer properties from a given {@link org.eclipse.ditto.model.connectivity.Connection}
 * configuration.
 */
final class ProducerPropertiesFactory {

    /**
     * Key of properties defined by org.apache.kafka.clients.producer.ProducerConfig inside producer internal config.
     * Defined by a previously used Alpakka kafka client.
     */
    private static final String KAFKA_CLIENTS_KEY = "kafka-clients";

    private static final Collection<KafkaSpecificConfig> SPECIFIC_CONFIGS =
            List.of(KafkaAuthenticationSpecificConfig.getInstance(), KafkaBootstrapServerSpecificConfig.getInstance());

    private final Connection connection;
    private final KafkaConfig kafkaConfig;
    private final String clientId;

    private ProducerPropertiesFactory(final Connection connection, final KafkaConfig kafkaConfig,
            final String clientId) {
        this.connection = checkNotNull(connection, "connection");
        this.kafkaConfig = checkNotNull(kafkaConfig, "Kafka config");
        this.clientId = checkNotNull(clientId, "clientId");
    }

    /**
     * Returns an instance of the ProducerSettings factory.
     *
     * @param connection the Kafka connection.
     * @param kafkaConfig the Kafka configuration settings.
     * @param clientId the client ID.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static ProducerPropertiesFactory getInstance(final Connection connection, final KafkaConfig kafkaConfig,
            final String clientId) {
        return new ProducerPropertiesFactory(connection, kafkaConfig, clientId);
    }

    Map<String, Object> getProducerProperties() {
        final HashMap<String, Object> producerProperties =
                configToProperties(kafkaConfig.getInternalProducerConfig().getConfig(KAFKA_CLIENTS_KEY));
        addMetadata(producerProperties);
        addSecurityProtocol(producerProperties);
        addSpecificConfig(producerProperties);
        return Collections.unmodifiableMap(producerProperties);
    }

    private void addMetadata(final HashMap<String, Object> properties) {
        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, clientId);
    }

    private void addSpecificConfig(final HashMap<String, Object> properties) {
        for (final KafkaSpecificConfig specificConfig : SPECIFIC_CONFIGS) {
            specificConfig.apply(properties, connection);
        }
    }

    private void addSecurityProtocol(final HashMap<String, Object> properties) {
        if (isConnectionAuthenticated()) {
            addAuthenticatedSecurityProtocol(properties);
        } else {
            addUnauthenticatedSecurityProtocol(properties);
        }
    }

    private boolean isConnectionAuthenticated() {
        final KafkaSpecificConfig authenticationSpecificConfig = KafkaAuthenticationSpecificConfig.getInstance();
        return authenticationSpecificConfig.isApplicable(connection);
    }

    private void addAuthenticatedSecurityProtocol(final HashMap<String, Object> properties) {
        if (isConnectionSecure()) {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        } else {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        }
    }

    private void addUnauthenticatedSecurityProtocol(final HashMap<String, Object> properties) {
        if (isConnectionSecure()) {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        } else {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
        }
    }

    private boolean isConnectionSecure() {
        return "ssl".equals(connection.getProtocol());
    }

    /**
     * Convert a structured Config into flat map from config paths to values.
     * Replicates Alpakka Kafka client's interpretation of client Config.
     *
     * @param config the Config object.
     * @return flat map from config paths to values.
     */
    private static HashMap<String, Object> configToProperties(final Config config) {
        final HashMap<String, Object> flattened = new HashMap<>();
        final Map<String, Object> unwrapped = config.root().unwrapped();
        flattenUnwrappedConfig(unwrapped, "", flattened);
        return flattened;
    }

    /**
     * Convert an unwrapped config into a flat properties map for the Kafka producer.
     *
     * @param unwrapped Result of {@code ConfigObject#unwrapped} containing structural maps.
     * @param prefix prefix of the config path.
     * @param accumulator accumulator in which the flat path-value definitions are written.
     */
    private static void flattenUnwrappedConfig(final Map<?, ?> unwrapped, final String prefix,
            final HashMap<String, Object> accumulator) {
        unwrapped.forEach((key, value) -> {
            final String path = prefix + key;
            if (value instanceof Map<?, ?>) {
                flattenUnwrappedConfig((Map<?, ?>) value, path + ".", accumulator);
            } else {
                accumulator.put(path, value);
            }
        });
    }
}
