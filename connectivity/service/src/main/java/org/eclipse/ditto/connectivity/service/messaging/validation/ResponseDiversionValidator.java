/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.connectivity.service.messaging.validation;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;

/**
 * Validator for response diversion configuration in connections.
 * This validator ensures that:
 * 1. The target connection ID is valid
 * 2. A connection doesn't divert to itself
 */
@Immutable
public final class ResponseDiversionValidator {

    private static Map<String, String> mappings;

    private ResponseDiversionValidator() {
        // no instantiation
    }

    public static void validate(final Connection connection, final DittoHeaders dittoHeaders) {
        validateResponseDiversionConnectionId(connection, dittoHeaders);
        connection.getSources().stream()
                .filter(source -> mappings.containsKey(DittoHeaderDefinition.DITTO_DIVERT_EXPECTED_RESPONSE_TYPES.getKey()))
                .findFirst()
                .ifPresent(source -> validateResponseTypes(source.getHeaderMapping().getMapping()
                        .get(DittoHeaderDefinition.DITTO_DIVERT_EXPECTED_RESPONSE_TYPES.getKey()), dittoHeaders));
    }

    /**
     * Validates the response diversion configuration of a connection.
     *
     * @param connection the connection to validate
     * @param dittoHeaders the headers to use for error reporting
     * @throws ConnectionConfigurationInvalidException if the diversion headers are invalid
     */
    public static void validateResponseDiversionConnectionId(final Connection connection, final DittoHeaders dittoHeaders) {
        final Set<String> targetConnectionId = connection.getSources().stream().filter(source -> {
                    mappings = source.getHeaderMapping().getMapping();
                    return mappings.containsKey(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey());

                }).map(source -> mappings.get(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey()))
                .collect(Collectors.toSet());


        targetConnectionId.forEach(targetId -> {
            // Check if target ID is not empty
            if (targetId.trim().isEmpty()) {
                throw ConnectionConfigurationInvalidException.newBuilder(
                                "The " + DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey() +
                                        " in headerMapping must not be empty")
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
            try {
                // Check if it's a valid connection ID format
                final ConnectionId parsedTargetId = ConnectionId.of(targetId);

                // Check for self-diversion
                if (parsedTargetId.equals(connection.getId())) {
                    throw ConnectionConfigurationInvalidException.newBuilder(
                                    "It is pointless to divert responses to the originating connection, it will receive the responses by default. " +
                                            "Remove the " + DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey() + " from the headerMapping of connection: " + connection.getId())
                            .dittoHeaders(dittoHeaders)
                            .build();
                }
            } catch (final ConnectionIdInvalidException e) {
                throw ConnectionConfigurationInvalidException.newBuilder(
                                "Invalid target connection ID format in " +
                                        DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey() + ": " + targetId)
                        .dittoHeaders(dittoHeaders)
                        .cause(e)
                        .build();
            }
        });
    }

    private static void validateResponseTypes(final String responseTypes, final DittoHeaders dittoHeaders) {
        if (!responseTypes.trim().isEmpty()) {
            final Set<String> validTypes = Arrays.stream(ResponseType.values())
                    .map(ResponseType::getName)
                    .collect(Collectors.toSet());
            Stream.of(responseTypes.split(","))
                    .map(String::trim)
                    .filter(type -> !type.isEmpty() && !validTypes.contains(type))
                    .findFirst()
                    .ifPresent(invalidType -> {
                        throw ConnectionConfigurationInvalidException.newBuilder(
                                        "Invalid response type in <"
                                                + DittoHeaderDefinition.DITTO_DIVERT_EXPECTED_RESPONSE_TYPES.getKey() + ">: "
                                                + invalidType + ". Valid types are: response, error, nack")
                                .dittoHeaders(dittoHeaders)
                                .build();
                    });
        }
    }
}
