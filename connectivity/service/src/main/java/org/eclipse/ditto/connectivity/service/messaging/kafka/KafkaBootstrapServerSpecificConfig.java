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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles all bootstrap server related configuration.
 * Expects the specific config of a connection to contain a non-empty list of bootstrap servers.
 * The list will be merged with the server found in the connection URI.
 */
final class KafkaBootstrapServerSpecificConfig implements KafkaSpecificConfig {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBootstrapServerSpecificConfig.class);

    private static final Pattern BOOTSTRAP_SERVERS_PATTERN = Pattern.compile(
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])(\\.([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9]))*\\.?:([1-9]|[1-5]?[0-9]{2,4}|6[1-4][0-9]{3}|65[1-4][0-9]{2}|655[1-2][0-9]|6553[1-5])[\\s,]*?)+$");
    private static final String INVALID_BOOTSTRAP_SERVERS =
            "The provided list of bootstrap servers ''{0}'' is not valid";

    private static final String SPECIFIC_CONFIG_BOOTSTRAP_SERVERS_KEY = "bootstrapServers";

    private static KafkaBootstrapServerSpecificConfig instance;

    private KafkaBootstrapServerSpecificConfig() {

    }

    public static KafkaBootstrapServerSpecificConfig getInstance() {
        if (null == instance) {
            instance = new KafkaBootstrapServerSpecificConfig();
        }
        return instance;
    }

    public String getBootstrapServers(final Connection connection) {
        final String mergedBootstrapServers;
        if (isValid(connection)) {
            final String bootstrapServerFromUri = getBootstrapServerFromUri(connection);
            final String additionalBootstrapServers = getBootstrapServersFromSpecificConfig(connection);
            mergedBootstrapServers =
                    mergeAdditionalBootstrapServers(bootstrapServerFromUri, additionalBootstrapServers);
        } else {
            // basically we should never end in this else-branch, since the connection should always contain bootstrap servers.
            // so this is just a fallback if something bad happens.
            LOG.warn(
                    "Kafka connection <{}> contains invalid configuration for its bootstrap servers. Either they are empty," +
                            " or don't match the pattern <host:port[,host:port]>. This should never happen as the connection should" +
                            " not have been stored with the invalid pattern.", connection.getId());
            mergedBootstrapServers = getBootstrapServerFromUri(connection);
        }
        return mergedBootstrapServers;
    }

    @Override
    public boolean isApplicable(final Connection connection) {
        // bootstrap servers have always to be part of the connection, so the config is always applicable.
        return true;
    }

    @Override
    public void validateOrThrow(final Connection connection, final DittoHeaders dittoHeaders) {
        if (!isValid(connection)) {
            final String message =
                    MessageFormat.format(INVALID_BOOTSTRAP_SERVERS, getBootstrapServersFromSpecificConfig(connection));
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public boolean isValid(final Connection connection) {
        return isValid(getBootstrapServersFromSpecificConfig(connection));
    }

    @Override
    public Map<String, String> apply(final Connection connection) {
        final String mergedBootstrapServers = getBootstrapServers(connection);
        return Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, mergedBootstrapServers);
    }

    private String getBootstrapServersFromSpecificConfig(final Connection connection) {
        return connection.getSpecificConfig().get(SPECIFIC_CONFIG_BOOTSTRAP_SERVERS_KEY);
    }

    private boolean isValid(@Nullable final String bootstrapServers) {
        return null != bootstrapServers
                && !bootstrapServers.isEmpty()
                && BOOTSTRAP_SERVERS_PATTERN.matcher(bootstrapServers).matches()
                && !bootstrapServers.trim().startsWith(",")
                && !bootstrapServers.trim().endsWith(",");
    }

    private static String mergeAdditionalBootstrapServers(final String serverWithoutProtocol,
            final String additionalBootstrapServers) {
        final Set<String> additionalServers = Arrays.stream(additionalBootstrapServers.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        additionalServers.add(serverWithoutProtocol);
        return String.join(",", additionalServers);
    }

    private static String getBootstrapServerFromUri(final Connection connection) {
        return connection.getHostname() + ":" + connection.getPort();
    }

}
