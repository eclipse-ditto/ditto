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

import static org.eclipse.ditto.placeholders.PlaceholderFactory.newHeadersPlaceholder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
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
        oauth2EnforceHttps = config.getOAuth2Config().shouldEnforceHttps();
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
        validateParallelism(connection.getSpecificConfig(), dittoHeaders);
        validateOmitBodyMethods(connection.getSpecificConfig(), dittoHeaders);
        validateCredentials(connection, dittoHeaders);
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
        validateTemplate(target.getAddress(), dittoHeaders,
                ConnectivityPlaceholders.newEntityPlaceholder(),
                ConnectivityPlaceholders.newThingPlaceholder(),
                ConnectivityPlaceholders.newTopicPathPlaceholder(),
                ConnectivityPlaceholders.newResourcePlaceholder(),
                ConnectivityPlaceholders.newTimePlaceholder(),
                newHeadersPlaceholder(),
                ConnectivityPlaceholders.newFeaturePlaceholder());
        validateTargetAddress(target.getAddress(), dittoHeaders, targetDescription);
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

    private static void validateParallelism(final Map<String, String> specificConfig, final DittoHeaders dittoHeaders) {
        final String parallelismString = specificConfig.get(HttpPushFactory.PARALLELISM_JSON_KEY);
        if (parallelismString != null) {
            try {
                final int parallelism = Integer.parseInt(parallelismString);
                if (parallelism <= 0) {
                    throw parallelismValidationFailed(parallelismString, dittoHeaders);
                }
            } catch (final NumberFormatException e) {
                throw parallelismValidationFailed(parallelismString, dittoHeaders);
            }
        }
    }

    private static void validateOmitBodyMethods(final Map<String, String> specificConfig,
            final DittoHeaders dittoHeaders) {

        final String omitBody = specificConfig.get(HttpPublisherActor.OMIT_REQUEST_BODY_CONFIG_KEY);
        if (omitBody != null && !omitBody.isEmpty()) {
            final String[] methodsArray = omitBody.split(",");
            for (final String method : methodsArray) {
                if (HttpMethods.lookup(method).isEmpty()) {
                    final String errorMessage = String.format("The configured value '%s' of '%s' is invalid. " +
                                    "It contains an invalid HTTP method: %s",
                            omitBody, HttpPublisherActor.OMIT_REQUEST_BODY_CONFIG_KEY, method);
                    throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                            .dittoHeaders(dittoHeaders)
                            .build();
                }
            }
        }
    }

    private void validateCredentials(final Connection connection, final DittoHeaders dittoHeaders) {
        connection.getCredentials().ifPresent(credentials -> {
            if (credentials instanceof OAuthClientCredentials) {
                final var oauthClientCredentials = (OAuthClientCredentials) credentials;
                final var uri = Uri.create(oauthClientCredentials.getTokenEndpoint());
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

    private static ConnectionConfigurationInvalidException parallelismValidationFailed(final String parallelismString,
            final DittoHeaders headers) {

        final String errorMessage = String.format("The configured value '%s' of '%s' is invalid. " +
                        "It must be a positive integer.",
                parallelismString,
                HttpPushFactory.PARALLELISM_JSON_KEY);
        return ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                .dittoHeaders(headers)
                .build();
    }

    static boolean isSecureScheme(final String scheme) {
        return HTTPS.equals(scheme);
    }

}
