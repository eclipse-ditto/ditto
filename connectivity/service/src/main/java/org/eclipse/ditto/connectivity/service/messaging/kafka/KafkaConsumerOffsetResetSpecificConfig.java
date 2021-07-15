/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;

/**
 * Allows to configure consumer offset reset via the specific config of a connection.
 */
final class KafkaConsumerOffsetResetSpecificConfig implements KafkaSpecificConfig {

    private static final String SPECIFIC_CONFIG_CONSUMER_OFFSET_KEY = "consumerOffsetReset";

    private static KafkaConsumerOffsetResetSpecificConfig instance;

    private KafkaConsumerOffsetResetSpecificConfig() {
    }

    static KafkaConsumerOffsetResetSpecificConfig getInstance() {
        if (instance == null) {
            instance = new KafkaConsumerOffsetResetSpecificConfig();
        }
        return instance;
    }

    @Override
    public boolean isApplicable(final Connection connection) {
        return !connection.getSources().isEmpty();
    }

    @Override
    public void validateOrThrow(final Connection connection, final DittoHeaders dittoHeaders) {
        if (!isValid(connection)) {
            final String message = MessageFormat.format(
                    "The connection configuration contains an invalid value for the consumer offset reset. " +
                            "Allowed values are: <{0}>", Arrays.toString(OffsetReset.values()));
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public boolean isValid(final Connection connection) {
        return getOffsetResetValueFromSpecificConfig(connection)
                .map(s -> OffsetReset.forNameIgnoreCase(s).isPresent())
                .orElse(true); // If no value configured this config is valid.
    }

    @Override
    public Map<String, String> apply(final Connection connection) {
        return getOffsetResetFromSpecificConfig(connection)
                .map(offsetReset -> Map.of(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset.name))
                .orElseGet(Map::of);
    }

    private Optional<OffsetReset> getOffsetResetFromSpecificConfig(final Connection connection) {
        return getOffsetResetValueFromSpecificConfig(connection)
                .flatMap(OffsetReset::forNameIgnoreCase);
    }

    private Optional<String> getOffsetResetValueFromSpecificConfig(final Connection connection) {
        return Optional.ofNullable(connection.getSpecificConfig().get(SPECIFIC_CONFIG_CONSUMER_OFFSET_KEY));
    }

    private enum OffsetReset {
        EARLIEST("earliest"),
        LATEST("latest");

        private final String name;

        OffsetReset(final String name) {
            this.name = name;
        }

        private static Optional<OffsetReset> forNameIgnoreCase(final String name) {
            return Arrays.stream(values())
                    .filter(offsetReset -> offsetReset.name.equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
