/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.gateway.api.GatewayDuplicateHeaderException;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMessage;
import akka.http.javadsl.server.RequestContext;

/**
 * This class provides a fluent API for building a CompletionStage that eventually supplies the {@link DittoHeaders} for
 * usage within the RootRoute.
 * Building the CompletionStage is designed to take place stepwise to guarantee that all required information is
 * provided in correct order.
 *
 * @since 1.1.0
 */
@NotThreadSafe
final class RootRouteHeadersStepBuilder {

    private final HeaderTranslator headerTranslator;
    private final QueryParametersToHeadersMap queryParamsToHeaders;
    private final CustomHeadersHandler customHeadersHandler;
    private final DittoHeadersValidator dittoHeadersValidator;

    private RootRouteHeadersStepBuilder(final HeaderTranslator headerTranslator,
            final QueryParametersToHeadersMap queryParamsToHeaders,
            final CustomHeadersHandler customHeadersHandler,
            final DittoHeadersValidator dittoHeadersValidator) {

        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.queryParamsToHeaders = checkNotNull(queryParamsToHeaders, "queryParamsToHeaders");
        this.customHeadersHandler = checkNotNull(customHeadersHandler, "customHeadersHandler");
        this.dittoHeadersValidator = checkNotNull(dittoHeadersValidator, "dittoHeadersValidator");
    }

    /**
     * Returns an instance of {@code RootRouteHeadersStepBuilder}.
     * The returned instance can be re-used for building a CompletionStage of DittoHeaders from scratch.
     *
     * @param headerTranslator translates request headers into valid DittoHeaders.
     * @param queryParamsToHeaders converts query parameters into a Map of header key-values pairs.
     * @param customHeadersHandler adds custom headers.
     * @param dittoHeadersValidator ensures that Ditto headers are small enough to be sent around the cluster.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static RootRouteHeadersStepBuilder getInstance(final HeaderTranslator headerTranslator,
            final QueryParametersToHeadersMap queryParamsToHeaders,
            final CustomHeadersHandler customHeadersHandler,
            final DittoHeadersValidator dittoHeadersValidator) {

        return new RootRouteHeadersStepBuilder(headerTranslator, queryParamsToHeaders, customHeadersHandler,
                dittoHeadersValidator);
    }

    /**
     * Sets the initial DittoHeaders builder.
     *
     * @param initialDittoHeadersBuilder initial DittoHeaders builder which might be pre-initialized with some header
     * values.
     * @return the next builder step.
     * @throws NullPointerException if {@code initialDittoHeadersBuilder} is {@code null}.
     */
    RequestContextStep withInitialDittoHeadersBuilder(final DittoHeadersBuilder<?, ?> initialDittoHeadersBuilder) {
        return new RequestContextStep(checkNotNull(initialDittoHeadersBuilder, "initialDittoHeadersBuilder"));
    }

    /**
     * Builder step for setting the RequestContext that provides the original request headers.
     */
    @NotThreadSafe
    final class RequestContextStep {

        private final DittoHeadersBuilder<?, ?> dittoHeadersBuilder;

        private RequestContextStep(final DittoHeadersBuilder<?, ?> dittoHeadersBuilder) {
            this.dittoHeadersBuilder = dittoHeadersBuilder;
        }

        /**
         * Sets the RequestContext that provides the original request headers which will be put to the eventual
         * DittoHeaders.
         *
         * @param requestContext the request context containing request headers to be set after header keys are
         * converted to lower-case.
         * @return the next builder step.
         * @throws NullPointerException if {@code requestContext} is {@code null}.
         * @throws GatewayDuplicateHeaderException if a collision of header keys occurred.
         */
        QueryParametersStep withRequestContext(final RequestContext requestContext) {
            checkNotNull(requestContext, "requestContext");
            final Map<String, String> filteredExternalHeaders = getFilteredExternalHeaders(requestContext.getRequest());
            return new QueryParametersStep(dittoHeadersBuilder, requestContext, filteredExternalHeaders);
        }

        private Map<String, String> getFilteredExternalHeaders(final HttpMessage httpRequest) {
            final Iterable<HttpHeader> headers = httpRequest.getHeaders();
            final Map<String, String> externalHeaders = StreamSupport.stream(headers.spliterator(), false)
                    .collect(Collectors.toMap(HttpHeader::lowercaseName, HttpHeader::value, (dv1, dv2) -> {
                        throw GatewayDuplicateHeaderException.newBuilder()
                                .dittoHeaders(dittoHeadersBuilder.build())
                                .build();
                    }));
            return headerTranslator.fromExternalHeaders(externalHeaders);
        }

    }

    /**
     * Builder step for setting the query parameters of the original request as part of the eventual DittoHeaders.
     */
    @NotThreadSafe
    final class QueryParametersStep {

        private final DittoHeadersBuilder<?, ?> dittoHeadersBuilder;
        private final RequestContext requestContext;
        private final Map<String, String> filteredExternalHeaders;

        private QueryParametersStep(final DittoHeadersBuilder<?, ?> dittoHeadersBuilder,
                final RequestContext requestContext,
                final Map<String, String> filteredExternalHeaders) {

            this.dittoHeadersBuilder = dittoHeadersBuilder;
            this.requestContext = requestContext;
            this.filteredExternalHeaders = filteredExternalHeaders;
        }

        /**
         * Sets the query parameters of the original request.
         * These parameters will be converted into headers and put to the eventual DittoHeaders.
         *
         * @param queryParameters the query parameters to be set.
         * @return the final builder step.
         * @throws NullPointerException if {@code queryParameters} is {@code null}.
         * @throws GatewayDuplicateHeaderException if the query parameters contain a key which is also contained in the
         * original request headers but which is associated with a divergent value.
         */
        BuildStep withQueryParameters(final Map<String, String> queryParameters) {
            checkNotNull(queryParameters, "queryParameters");
            final Map<String, String> externalQueryParams = headerTranslator.fromExternalHeaders(queryParameters);
            final Map<String, String> headersFromQueryParameters = queryParamsToHeaders.apply(externalQueryParams);
            avoidConflictingHeaders(headersFromQueryParameters);
            dittoHeadersBuilder.putHeaders(filteredExternalHeaders);
            dittoHeadersBuilder.putHeaders(headersFromQueryParameters);

            return new BuildStep(dittoHeadersBuilder, requestContext);
        }

        private void avoidConflictingHeaders(final Map<String, String> headersFromQueryParameters) {
            headersFromQueryParameters.forEach((key, value) -> {
                @Nullable final String externalHeaderValue = filteredExternalHeaders.get(key);
                if (null != externalHeaderValue && !value.equals(externalHeaderValue)) {
                    throw getDuplicateHeaderException(key);
                }
            });
        }

        private GatewayDuplicateHeaderException getDuplicateHeaderException(final String headerKey) {
            final String msgPattern = "<{0}> was provided as header as well as query parameter with divergent values!";
            return GatewayDuplicateHeaderException.newBuilder()
                    .message(MessageFormat.format(msgPattern, headerKey))
                    .dittoHeaders(dittoHeadersBuilder.build())
                    .build();
        }

    }

    /**
     * Final builder step which provides a CompletionStage of the eventual DittoHeaders.
     */
    @NotThreadSafe
    final class BuildStep {

        private final DittoHeadersBuilder<?, ?> dittoHeadersBuilder;
        private final RequestContext requestContext;

        private BuildStep(final DittoHeadersBuilder<?, ?> dittoHeadersBuilder, final RequestContext requestContext) {
            this.dittoHeadersBuilder = dittoHeadersBuilder;
            this.requestContext = requestContext;
        }

        /**
         * Builds a CompletionStage providing the eventual DittoHeaders.
         * The eventual DittoHeaders are guaranteed to fit within the size limits of cluster messages.
         *
         * @param requestType the request type which is required for adding custom headers.
         * @return a CompletionStage providing the eventual DittoHeaders.
         * @throws NullPointerException if {@code requestType} is {@code null}.
         */
        CompletionStage<DittoHeaders> build(final CustomHeadersHandler.RequestType requestType) {
            checkNotNull(requestType, "requestType");

            final DittoHeaders dittoDefaultHeaders = dittoHeadersBuilder.build();

            // At this point it is ensured that a correlation ID was set
            final String correlationId = dittoDefaultHeaders.getCorrelationId().orElseThrow();

            CompletionStage<DittoHeaders> result = customHeadersHandler.handleCustomHeaders(correlationId,
                    requestContext,
                    requestType,
                    dittoDefaultHeaders);

            return result.thenCompose(dittoHeadersValidator::validate);
        }

    }

}
