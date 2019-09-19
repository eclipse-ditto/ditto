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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.validation.AbstractProtocolValidator;

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

    private HttpPushValidator() {}

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
    public void validate(final Connection connection, final DittoHeaders dittoHeaders,
            final @Nullable ConnectionConfig config) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "HTTP");
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validateHttpMethod(connection.getSpecificConfig().get(HttpPushFactory.METHOD), dittoHeaders);
        validateParallelism(connection.getSpecificConfig(), dittoHeaders, config);
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
    }

    private void validateHttpMethod(@Nullable final String methodName, final DittoHeaders dittoHeaders) {
        if (methodName != null) {
            final Optional<HttpMethod> method = HttpMethods.lookup(methodName);
            if (!method.isPresent() || !SUPPORTED_METHODS.contains(method.get())) {
                final String errorMessage = String.format(
                        "The method '%s' is not supported. Supported methods are %s.",
                        methodName, SUPPORTED_METHOD_NAMES);
                throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        }
    }

    private void validateParallelism(final Map<String, String> specificConfig, final DittoHeaders dittoHeaders,
            @Nullable final ConnectionConfig config) {
        final String parallelismString = specificConfig.get(HttpPushFactory.PARALLELISM);
        if (parallelismString != null) {
            try {
                final int parallelism = Integer.parseInt(parallelismString);
                if (parallelism <= 0) {
                    // skip to the catch block
                    throw new NumberFormatException();
                }
                if (config != null && parallelism > config.getHttpPushConfig().getMaxParallelism()) {
                    final String errorMessage = String.format("The configured value '%s' of '%s' is invalid. " +
                                    "It must be a positive integer between 1 and %d.",
                            parallelismString,
                            HttpPushFactory.PARALLELISM,
                            config.getHttpPushConfig().getMaxParallelism()
                    );
                    throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                            .dittoHeaders(dittoHeaders)
                            .build();
                }
            } catch (final NumberFormatException e) {
                final String errorMessage = String.format(
                        "The configured value '%s' of '%s' is invalid. It must be a positive integer.",
                        parallelismString, HttpPushFactory.PARALLELISM);
                throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        }
    }

    static boolean isSecureScheme(final String scheme) {
        return HTTPS.equals(scheme);
    }
}
