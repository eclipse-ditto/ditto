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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;

/**
 * Adapter for mapping a {@link ErrorResponse} to and from an {@link Adaptable}.
 *
 * @param <T> the type of the {@link ErrorResponse}
 */
public abstract class AbstractErrorResponseAdapter<T extends ErrorResponse<T>> implements ErrorResponseAdapter<T> {

    private final HeaderTranslator headerTranslator;
    private final ErrorRegistry<DittoRuntimeException> errorRegistry;

    protected AbstractErrorResponseAdapter(final HeaderTranslator headerTranslator,
            final ErrorRegistry<DittoRuntimeException> errorRegistry) {
        this.headerTranslator = headerTranslator;
        this.errorRegistry = errorRegistry;
    }

    /**
     * Parse an error JSON using an error registry, or construct a generic {@code DittoRuntimeException} if the error
     * code is not in the registry.
     *
     * @param errorJson JSON representation of the error.
     * @param dittoHeaders Headers of the error.
     * @param errorRegistry The error registry.
     * @return The parsed {@code DittoRuntimeException}.
     *
     * @since 1.1.0
     */
    public static DittoRuntimeException parseWithErrorRegistry(final JsonObject errorJson,
            final DittoHeaders dittoHeaders,
            final ErrorRegistry<?> errorRegistry) {
        try {
            return errorRegistry.parse(errorJson, dittoHeaders);
        } catch (final JsonTypeNotParsableException e) {
            return DittoRuntimeException.fromUnknownErrorJson(errorJson, dittoHeaders)
                    .orElseThrow(() -> e);
        }

    }

    @Override
    public T fromAdaptable(final Adaptable adaptable) {
        final DittoHeaders dittoHeaders = DittoHeaders.of(
                headerTranslator.fromExternalHeaders(adaptable.getHeaders().orElse(DittoHeaders.empty())));
        final TopicPath topicPath = adaptable.getTopicPath();

        final DittoRuntimeException dittoRuntimeException = adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(jsonObject -> parseWithErrorRegistry(jsonObject, dittoHeaders, errorRegistry))
                .orElseThrow(() -> new JsonMissingFieldException(ThingCommandResponse.JsonFields.PAYLOAD));

        return buildErrorResponse(topicPath, dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    @Override
    public Adaptable toAdaptable(final T errorResponse, final TopicPath.Channel channel) {

        final TopicPathBuilder topicPathBuilder = getTopicPathBuilder(errorResponse);

        final Payload payload = Payload.newBuilder(errorResponse.getResourcePath())
                .withStatus(errorResponse.getStatusCode())
                .withValue(errorResponse.toJson(errorResponse.getImplementedSchemaVersion())
                        .getValue(CommandResponse.JsonFields.PAYLOAD)
                        .orElse(JsonFactory.nullObject())) // only use the error payload
                .build();

        final TopicPathBuildable topicPathBuildable;
        if (channel == TopicPath.Channel.TWIN) {
            topicPathBuildable = topicPathBuilder.twin().errors();
        } else if (channel == TopicPath.Channel.LIVE) {
            topicPathBuildable = topicPathBuilder.live().errors();
        } else if (channel == TopicPath.Channel.NONE) {
            topicPathBuildable = topicPathBuilder.none().errors();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }

        final DittoHeaders responseHeaders =
                ProtocolFactory.newHeadersWithDittoContentType(errorResponse.getDittoHeaders());

        return Adaptable.newBuilder(topicPathBuildable.build())
                .withPayload(payload)
                .withHeaders(DittoHeaders.of(headerTranslator.toExternalHeaders(responseHeaders)))
                .build();
    }

    /**
     * Implementations must provide a {@link TopicPathBuilder} for the given {@code errorResponse}.
     *
     * @param errorResponse the processed error response
     * @return the {@link TopicPathBuilder} used to processed the given {@code errorResponse}
     */
    public abstract TopicPathBuilder getTopicPathBuilder(final T errorResponse);

    /**
     * Implementations can build the {@link ErrorResponse} from the given parameters.
     *
     * @param topicPath the {@link TopicPath} used to build the error response
     * @param exception the {@link DittoRuntimeException} used to build the error response
     * @param dittoHeaders the {@link DittoHeaders} used to build the error response
     * @return the built error response
     */
    public abstract T buildErrorResponse(final TopicPath topicPath, final DittoRuntimeException exception,
            final DittoHeaders dittoHeaders);
}
