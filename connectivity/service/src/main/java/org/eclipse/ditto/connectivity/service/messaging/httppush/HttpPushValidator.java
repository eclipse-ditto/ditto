/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.eclipse.ditto.connectivity.service.messaging.Resolvers;
import org.eclipse.ditto.connectivity.service.messaging.validation.AbstractProtocolValidator;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.Uri;

/**
 * Validation of http-push connections.
 */
public final class HttpPushValidator extends AbstractProtocolValidator {

    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final Collection<String> ACCEPTED_SCHEMES = List.of(HTTP, HTTPS);
    private static final Collection<String> SECURE_SCHEMES = List.of(HTTPS);

    private static final Collection<HttpMethod> SUPPORTED_METHODS =
            List.of(HttpMethods.PUT, HttpMethods.PATCH, HttpMethods.POST, HttpMethods.GET, HttpMethods.DELETE);

    private static final String SUPPORTED_METHOD_NAMES = SUPPORTED_METHODS.stream()
            .map(HttpMethod::name)
            .collect(Collectors.joining(", "));

    static final Duration MAX_IDLE_TIMEOUT = Duration.of(60, ChronoUnit.SECONDS);

    private final HttpPushConfig httpPushConfig;
    private final boolean oauth2EnforceHttps;

    /**
     * Create a new validator for http-push connections.
     *
     * @param config the HTTP Push config.
     * @return the validator.
     */
    public static HttpPushValidator newInstance(final HttpPushConfig config) {
        return new HttpPushValidator(config);
    }

    private HttpPushValidator(final HttpPushConfig config) {
        httpPushConfig = config;
        oauth2EnforceHttps = httpPushConfig.getOAuth2Config().shouldEnforceHttps();
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.HTTP_PUSH;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "HTTP");
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validatePayloadMappings(connection, actorSystem, connectivityConfig, dittoHeaders);
        validateCredentials(connection, dittoHeaders);
        validateSpecificConfig(connection, dittoHeaders);
    }

    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        final String errorMessage =
                String.format("A connection of type '%s' may not have any source.", type().getName());
        throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        validateHeaderMapping(target.getHeaderMapping(), dittoHeaders);
        validateTemplate(target.getAddress(), dittoHeaders, Resolvers.getPlaceholders());
        validateTargetAddress(target.getAddress(), dittoHeaders, targetDescription);
        validateExtraFields(target);
    }

    private static void validateTargetAddress(final String targetAddress,
            final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {

        final String[] methodAndPath = HttpPublishTarget.splitMethodAndPath(targetAddress);
        if (methodAndPath.length == 2) {
            validateHttpMethod(methodAndPath[0], dittoHeaders, targetDescription);
        } else {
            final String message =
                    String.format("%s: Target address has invalid format. Expect '%s', for example '%s'.",
                            targetDescription.get(), "<VERB>:/<path>", "POST:/api");
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .description("Supported methods are: " + SUPPORTED_METHOD_NAMES)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static void validateHttpMethod(final String methodName,
            final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescriptor) {

        final Optional<HttpMethod> method = HttpMethods.lookup(methodName);
        if (method.isEmpty() || !SUPPORTED_METHODS.contains(method.get())) {
            final String errorMessage = String.format(
                    "%s: The method '%s' is not supported", targetDescriptor.get(), methodName);
            throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                    .description("Supported methods are: " + SUPPORTED_METHOD_NAMES)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private void validateCredentials(final Connection connection, final DittoHeaders dittoHeaders) {
        connection.getCredentials().ifPresent(credentials -> {
            if (credentials instanceof OAuthClientCredentials oAuthClientCredentials) {
                final var uri = Uri.create(oAuthClientCredentials.getTokenEndpoint());
                if (oauth2EnforceHttps && !isSecureScheme(uri.getScheme())) {
                    final var errorMessage = "The OAuth2 token endpoint must be accessed via HTTPS " +
                            "in order not to transmit the client secret in plain text.";
                    throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                            .dittoHeaders(dittoHeaders)
                            .build();
                }
            }
        });
    }

    private void validateSpecificConfig(final Connection connection, final DittoHeaders dittoHeaders) {
        final HttpPushSpecificConfig
                httpPushSpecificConfig = HttpPushSpecificConfig.fromConnection(connection, httpPushConfig);
        validateIdleTimeout(httpPushSpecificConfig.idleTimeout(), dittoHeaders);
        validateParallelism(httpPushSpecificConfig.parallelism(), dittoHeaders);
        validateOmitBodyMethods(httpPushSpecificConfig.omitRequestBody(), dittoHeaders);
    }

    private static void validateIdleTimeout(final Duration idleTimeout, final DittoHeaders dittoHeaders) {
        if (idleTimeout.isNegative() || idleTimeout.compareTo(MAX_IDLE_TIMEOUT) > 0) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder("Idle timeout '" + idleTimeout.toSeconds() +
                            "' is not within the allowed range of [0, " + MAX_IDLE_TIMEOUT.toSeconds() + "] seconds.")
                    .description("Please adjust the timeout to be within the allowed range.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static void validateParallelism(final int parallelism, final DittoHeaders dittoHeaders) {
        try {
            if (parallelism <= 0) {
                throw parallelismValidationFailed(parallelism, dittoHeaders);
            }
        } catch (final NumberFormatException e) {
            throw parallelismValidationFailed(parallelism, dittoHeaders);
        }
    }

    private static void validateOmitBodyMethods(final List<String> omitBodyMethods,
            final DittoHeaders dittoHeaders) {

        if (!omitBodyMethods.isEmpty()) {
            for (final String method : omitBodyMethods) {
                if (!method.equals("") && HttpMethods.lookup(method).isEmpty()) {
                    final String errorMessage = String.format("The configured value '%s' of '%s' is invalid. " +
                                    "It contains an invalid HTTP method: %s",
                            omitBodyMethods, HttpPushSpecificConfig.OMIT_REQUEST_BODY, method);
                    throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                            .dittoHeaders(dittoHeaders)
                            .build();
                }
            }
        }
    }

    private static ConnectionConfigurationInvalidException parallelismValidationFailed(final int parallelism,
            final DittoHeaders headers) {

        final String errorMessage = String.format("The configured value '%s' of '%s' is invalid. " +
                        "It must be a positive integer.",
                parallelism,
                HttpPushSpecificConfig.PARALLELISM);
        return ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                .dittoHeaders(headers)
                .build();
    }

    static boolean isSecureScheme(final String scheme) {
        return HTTPS.equals(scheme);
    }

}
