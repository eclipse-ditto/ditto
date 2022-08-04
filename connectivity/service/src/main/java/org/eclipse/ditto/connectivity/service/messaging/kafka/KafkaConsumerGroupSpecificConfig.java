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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

/**
 * Allows to configure a consumer group ID via the specific config of a connection.
 */
final class KafkaConsumerGroupSpecificConfig implements KafkaSpecificConfig {

    private static final String GROUP_ID_ALLOWED_CHARACTERS = "[a-zA-Z0-9\\._\\-]";
    private static final Pattern GROUP_ID_VALIDATION_PATTERN = Pattern.compile(GROUP_ID_ALLOWED_CHARACTERS + "+");
    private static final String GROUP_ID_SPECIFIC_CONFIG_KEY = "groupId";

    private static KafkaConsumerGroupSpecificConfig instance;

    private KafkaConsumerGroupSpecificConfig() {
    }

    static KafkaConsumerGroupSpecificConfig getInstance() {
        if (instance == null) {
            instance = new KafkaConsumerGroupSpecificConfig();
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
            final String groupId = getGroupId(connection).orElse("");
            final String message = MessageFormat.format(
                    "The connection configuration contains an invalid value for the consumer group ID. " +
                            "Allowed Characters are: <{1}>", groupId, GROUP_ID_ALLOWED_CHARACTERS);
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public boolean isValid(final Connection connection) {
        final Optional<String> optionalGroupId = getGroupId(connection);
        if (optionalGroupId.isPresent()) {
            final String groupId = optionalGroupId.get();
            return GROUP_ID_VALIDATION_PATTERN.matcher(groupId).matches();
        }
        return true;
    }

    @Override
    public Map<String, String> apply(final Connection connection) {
        return getGroupId(connection)
                .map(groupId -> Map.of(ConsumerConfig.GROUP_ID_CONFIG, groupId))
                .orElseGet(Map::of);
    }

    private Optional<String> getGroupId(final Connection connection) {
        final var placeholderResolver =
                PlaceholderFactory.newExpressionResolver(ConnectivityPlaceholders.newConnectionIdPlaceholder(),
                        connection.getId());
        return Optional.ofNullable(connection.getSpecificConfig().get(GROUP_ID_SPECIFIC_CONFIG_KEY))
                .map(groupId -> PlaceholderFilter.apply(groupId, placeholderResolver));
    }

}
