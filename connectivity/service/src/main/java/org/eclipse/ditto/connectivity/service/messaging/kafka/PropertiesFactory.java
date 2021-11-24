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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteBufferDeserializer;
import org.apache.kafka.common.serialization.ByteBufferSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.KafkaConfig;

import com.typesafe.config.Config;

import akka.kafka.CommitterSettings;
import akka.kafka.ConnectionCheckerSettings;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;

/**
 * Creates Kafka properties from a given {@link org.eclipse.ditto.connectivity.model.Connection} configuration.
 */
final class PropertiesFactory {

    private static final Collection<KafkaSpecificConfig> COMMON_SPECIFIC_CONFIGS =
            List.of(KafkaAuthenticationSpecificConfig.getInstance(), KafkaBootstrapServerSpecificConfig.getInstance());

    private static final Collection<KafkaSpecificConfig> CONSUMER_SPECIFIC_CONFIGS;
    private static final Collection<KafkaSpecificConfig> PRODUCER_SPECIFIC_CONFIGS;

    static {
        final List<KafkaSpecificConfig> consumerSpecificConfigs = new ArrayList<>(COMMON_SPECIFIC_CONFIGS);
        consumerSpecificConfigs.add(KafkaConsumerGroupSpecificConfig.getInstance());
        consumerSpecificConfigs.add(KafkaConsumerOffsetResetSpecificConfig.getInstance());
        CONSUMER_SPECIFIC_CONFIGS = List.copyOf(consumerSpecificConfigs);
        PRODUCER_SPECIFIC_CONFIGS = List.copyOf(COMMON_SPECIFIC_CONFIGS);
    }

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
    ConsumerSettings<String, ByteBuffer> getConsumerSettings(final boolean dryRun) {
        final Config alpakkaConfig = this.config.getConsumerConfig().getAlpakkaConfig();
        final ConnectionCheckerSettings connectionCheckerSettings =
                ConnectionCheckerSettings.apply(alpakkaConfig.getConfig("connection-checker"));
        final ConsumerSettings<String, ByteBuffer> consumerSettings =
                ConsumerSettings.apply(alpakkaConfig, new StringDeserializer(), new ByteBufferDeserializer())
                        .withBootstrapServers(bootstrapServers)
                        .withGroupId(connection.getId().toString())
                        .withClientId(clientId + "-consumer")
                        .withProperties(getConsumerSpecificConfigProperties())
                        .withProperties(getSecurityProtocolProperties())
                        .withConnectionChecker(connectionCheckerSettings);

        // disable auto commit in dry run mode
        return dryRun ? consumerSettings.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false") :
                consumerSettings;
    }

    CommitterSettings getCommitterSettings() {
        final Config committerConfig = this.config.getCommitterConfig().getAlpakkaConfig();
        return CommitterSettings.apply(committerConfig);
    }

    ProducerSettings<String, ByteBuffer> getProducerSettings() {
        final Config alpakkaConfig = this.config.getProducerConfig().getAlpakkaConfig();
        return ProducerSettings.apply(alpakkaConfig, new StringSerializer(), new ByteBufferSerializer())
                .withBootstrapServers(bootstrapServers)
                .withProperties(getClientIdProperties())
                .withProperties(getProducerSpecificConfigProperties())
                .withProperties(getSecurityProtocolProperties());
    }

    private Map<String, String> getClientIdProperties() {
        return Map.of(CommonClientConfigs.CLIENT_ID_CONFIG, clientId + "-producer");
    }

    private Map<String, String> getConsumerSpecificConfigProperties() {
        final Map<String, String> properties = new HashMap<>();
        for (final KafkaSpecificConfig specificConfig : CONSUMER_SPECIFIC_CONFIGS) {
            properties.putAll(specificConfig.apply(connection));
        }
        return properties;
    }

    private Map<String, String> getProducerSpecificConfigProperties() {
        final Map<String, String> properties = new HashMap<>();
        for (final KafkaSpecificConfig specificConfig : PRODUCER_SPECIFIC_CONFIGS) {
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
