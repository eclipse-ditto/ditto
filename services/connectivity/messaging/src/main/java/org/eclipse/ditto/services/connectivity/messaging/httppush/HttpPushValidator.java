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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newThingPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newTopicPathPlaceholder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.validation.AbstractProtocolValidator;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;

/**
 * Validation of http-push connections.
 */
public final class HttpPushValidator extends AbstractProtocolValidator {

    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final Collection<String> ACCEPTED_SCHEMES = Collections.unmodifiableList(Arrays.asList(HTTP, HTTPS));
    private static final Collection<String> SECURE_SCHEMES = Collections.singletonList(HTTPS);

    private static final Collection<HttpMethod> SUPPORTED_METHODS =
            Collections.unmodifiableList(Arrays.asList(HttpMethods.PUT, HttpMethods.PATCH, HttpMethods.POST));

    private static final String SUPPORTED_METHOD_NAMES = SUPPORTED_METHODS.stream()
            .map(HttpMethod::name)
            .collect(Collectors.joining(", "));

    /**
     * Create a new validator for http-push connections.
     *
     * @return the validator.
     */
    public static HttpPushValidator newInstance() {
        return new HttpPushValidator();
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.HTTP_PUSH;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "HTTP");
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validateMappingContext(connection, actorSystem, dittoHeaders);
        validateParallelism(connection.getSpecificConfig(), dittoHeaders);
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
        target.getHeaderMapping().ifPresent(mapping -> validateHeaderMapping(mapping, dittoHeaders));
        validateTemplate(target.getAddress(), dittoHeaders, newThingPlaceholder(), newTopicPathPlaceholder(),
                newHeadersPlaceholder());
        validateTargetAddress(target.getAddress(), dittoHeaders, targetDescription);
    }

    private void validateTargetAddress(final String targetAddress, final DittoHeaders dittoHeaders,
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

    private void validateHttpMethod(final String methodName, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescriptor) {
        final Optional<HttpMethod> method = HttpMethods.lookup(methodName);
        if (!method.isPresent() || !SUPPORTED_METHODS.contains(method.get())) {
            final String errorMessage = String.format(
                    "%s: The method '%s' is not supported", targetDescriptor.get(), methodName);
            throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                    .description("Supported methods are: " + SUPPORTED_METHOD_NAMES)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private void validateParallelism(final Map<String, String> specificConfig, final DittoHeaders dittoHeaders) {

        final String parallelismString = specificConfig.get(HttpPushFactory.PARALLELISM);
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

    private static ConnectionConfigurationInvalidException parallelismValidationFailed(final String parallelismString,
            final DittoHeaders headers) {

        final String errorMessage = String.format("The configured value '%s' of '%s' is invalid. " +
                        "It must be a positive integer.",
                parallelismString,
                HttpPushFactory.PARALLELISM);
        return ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                .dittoHeaders(headers)
                .build();
    }

    static boolean isSecureScheme(final String scheme) {
        return HTTPS.equals(scheme);
    }
}
