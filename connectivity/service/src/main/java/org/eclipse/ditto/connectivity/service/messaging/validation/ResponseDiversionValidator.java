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

import static org.eclipse.ditto.connectivity.service.messaging.BaseClientActor.IS_DIVERSION_SOURCE;
import static org.eclipse.ditto.connectivity.service.messaging.BaseClientActor.IS_DIVERSION_SOURCE_DEFAULT;
import static org.eclipse.ditto.connectivity.service.messaging.BaseClientActor.IS_DIVERSION_TARGET;
import static org.eclipse.ditto.connectivity.service.messaging.BaseClientActor.IS_DIVERSION_TARGET_DEFAULT;

import java.util.Arrays;
import java.util.List;
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
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.ResponseDiversionInterceptor;

/**
 * Validator for response diversion configuration in connections.
 * This validator ensures that:
 * 1. The target connection ID is valid
 * 2. A connection doesn't divert to itself
 */
@Immutable
public final class ResponseDiversionValidator {


    private ResponseDiversionValidator() {
        // no instantiation
    }

    /**
     * Validates the response diversion configuration of a connection.
     *
     * @param connection the connection to validate
     * @param dittoHeaders the headers to use for error reporting
     * @throws ConnectionConfigurationInvalidException if the diversion headers are invalid
     */
    public static void validate(final Connection connection, final DittoHeaders dittoHeaders) {
        validateResponseDiversionTypes(connection, dittoHeaders);
        validateResponseDiversionConnectionId(connection, dittoHeaders);
        validateAuthorizedDiversionSources(connection, dittoHeaders);
    }

    private static void validateAuthorizedDiversionSources(final Connection connection,
            final DittoHeaders dittoHeaders) {
        boolean isDiversionTarget = check(connection, IS_DIVERSION_TARGET, IS_DIVERSION_TARGET_DEFAULT, dittoHeaders);
        if (isDiversionTarget) {
            final Set<ConnectionId> authorizedSources = connection.getSpecificConfig()
                    .getOrDefault(ResponseDiversionInterceptor.AUTHORIZED_CONNECTIONS_AS_SOURCES, "")
                    .trim()
                    .isEmpty() ? Set.of() : Stream.of(connection.getSpecificConfig()
                            .get(ResponseDiversionInterceptor.AUTHORIZED_CONNECTIONS_AS_SOURCES).split(","))
                    .map(String::trim)
                    .map(ConnectionId::of)
                    .collect(Collectors.toSet());
        }
    }

    private static boolean check(final Connection connection, final String configKey, final String defaultValue,
            final DittoHeaders dittoHeaders) {
        final String isSourseString = connection.getSpecificConfig().getOrDefault(configKey, defaultValue);
        return Boolean.parseBoolean(isSourseString);
    }

    private static void validateResponseDiversionTypes(final Connection connection, final DittoHeaders dittoHeaders) {
        final boolean isDiversionSource =
                check(connection, IS_DIVERSION_SOURCE, IS_DIVERSION_SOURCE_DEFAULT, dittoHeaders);
        if (isDiversionSource) {
            final List<Source> list = connection.getSources().stream()
                    .filter(source -> source.getHeaderMapping().getMapping().containsKey(
                            DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey()))
                    .peek(source -> validateResponseTypes(source.getHeaderMapping().getMapping()
                            .get(DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey()), dittoHeaders))
                    .toList();

        }
    }

    /**
     * Validates the target connection ID for response diversion.
     *
     * @param connection the connection to validate
     * @param dittoHeaders the headers to use for error reporting
     * @return true if the target connection ID is valid, false if no target connection ID is specified
     * @throws ConnectionConfigurationInvalidException if the target connection ID is invalid
     */
    public static boolean validateResponseDiversionConnectionId(final Connection connection,
            final DittoHeaders dittoHeaders) {
        final Set<String> targetConnectionId = connection.getSources()
                .stream()
                .filter(source -> source.getHeaderMapping()
                        .getMapping()
                        .containsKey(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey()))
                .map(source -> source.getHeaderMapping()
                        .getMapping()
                        .get(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey()))
                .collect(Collectors.toSet());


        targetConnectionId.forEach(targetId -> {
            // Check if target ID is not empty
            if (targetId.trim().isEmpty()) {
                throw ConnectionConfigurationInvalidException.newBuilder(
                                "The " + DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey() +
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
                                            "Remove the " + DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey() +
                                            " from the headerMapping of connection: " + connection.getId())
                            .dittoHeaders(dittoHeaders)
                            .build();
                }
            } catch (final ConnectionIdInvalidException e) {
                throw ConnectionConfigurationInvalidException.newBuilder(
                                "Invalid target connection ID format in " +
                                        DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey() + ": " + targetId)
                        .dittoHeaders(dittoHeaders)
                        .cause(e)
                        .build();
            }
        });
        return !targetConnectionId.isEmpty();
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
                                                + DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey() + ">: "
                                                + invalidType + ". Valid types are: response, error, nack")
                                .dittoHeaders(dittoHeaders)
                                .build();
                    });
        }
    }
}
