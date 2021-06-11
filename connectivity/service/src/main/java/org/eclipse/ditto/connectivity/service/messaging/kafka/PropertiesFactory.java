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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.KafkaConfig;

import com.typesafe.config.Config;

import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;

/**
 * Creates Kafka properties from a given {@link org.eclipse.ditto.connectivity.model.Connection} configuration.
 */
final class PropertiesFactory {

    private static final Collection<KafkaSpecificConfig> SPECIFIC_CONFIGS =
            List.of(KafkaAuthenticationSpecificConfig.getInstance(), KafkaBootstrapServerSpecificConfig.getInstance());

    private final Connection connection;
    private final KafkaConfig config;
    private final String clientId;
    private final String bootstrapServers;

    private PropertiesFactory(final Connection connection, final KafkaConfig config, final String clientId) {
        this.connection = checkNotNull(connection, "connection");
        this.config = checkNotNull(config, "config");
        this.clientId = checkNotNull(clientId, "clientId");
        this.bootstrapServers = KafkaBootstrapServerSpecificConfig.getInstance().getBootstrapServers(connection);
    }

    /**
     * Returns an instance of the factory.
     *
     * @param connection the Kafka connection.
     * @param config the Kafka configuration settings.
     * @param clientId the client ID.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PropertiesFactory newInstance(final Connection connection, final KafkaConfig config, final String clientId) {
        return new PropertiesFactory(connection, config, clientId);
    }

    /**
     * Returns settings for a kafka consumer.
     *
     * @return the settings.
     */
    ConsumerSettings<String, String> getConsumerSettings(final boolean dryRun) {
        final ConsumerSettings<String, String> consumerSettings =
                ConsumerSettings.apply(config.getConsumerConfig(), new StringDeserializer(), new StringDeserializer())
                        .withBootstrapServers(bootstrapServers)
                        .withGroupId(connection.getId().toString())
                        .withClientId(clientId + "-consumer");

        // disable auto commit in dry run mode
        return dryRun ? consumerSettings.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false") :
                consumerSettings;
    }

    ProducerSettings<String, String> getProducerSettings() {
        return ProducerSettings.apply(config.getProducerConfig(), new StringSerializer(), new StringSerializer())
                .withBootstrapServers(bootstrapServers)
                .withProperties(getClientIdProperties())
                .withProperties(getSpecificConfigProperties())
                .withProperties(getSecurityProtocolProperties());
    }

    private Map<String, String> getClientIdProperties() {
        return Map.of(CommonClientConfigs.CLIENT_ID_CONFIG, clientId + "-producer");
    }

    private Map<String, String> getSpecificConfigProperties() {
        final Map<String, String> properties = new HashMap<>();
        for (final KafkaSpecificConfig specificConfig : SPECIFIC_CONFIGS) {
            properties.putAll(specificConfig.apply(connection));
        }
        return properties;
    }

    private Map<String, String> getSecurityProtocolProperties() {
        if (isConnectionAuthenticated()) {
            return addAuthenticatedSecurityProtocol();
        } else {
            return addUnauthenticatedSecurityProtocol();
        }
    }

    private boolean isConnectionAuthenticated() {
        final KafkaSpecificConfig authenticationSpecificConfig = KafkaAuthenticationSpecificConfig.getInstance();
        return authenticationSpecificConfig.isApplicable(connection);
    }

    private Map<String, String> addAuthenticatedSecurityProtocol() {
        if (isConnectionSecure()) {
            return Map.of(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        } else {
            return Map.of(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        }
    }

    private Map<String, String> addUnauthenticatedSecurityProtocol() {
        if (isConnectionSecure()) {
            return Map.of(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        } else {
            return Map.of(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
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
     * Convert an unwrapped config into a flat properties map.
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