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
package org.eclipse.ditto.protocol.adapter;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuildable;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.adapter.HeadersFromTopicPath.Extractor;

/**
 * Adapter for mapping a {@link ErrorResponse} to and from an {@link org.eclipse.ditto.protocol.Adaptable}.
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
     * @since 1.1.0
     */
    public static DittoRuntimeException parseWithErrorRegistry(final JsonObject errorJson,
            final DittoHeaders dittoHeaders, final ErrorRegistry<?> errorRegistry) {

        return errorRegistry.parse(errorJson, dittoHeaders);
    }

    @Override
    public T fromAdaptable(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();

        final DittoHeaders dittoHeadersFromExternal =
                DittoHeaders.of(headerTranslator.fromExternalHeaders(adaptable.getDittoHeaders()));

        final DittoHeaders dittoHeaders = HeadersFromTopicPath.injectHeaders(dittoHeadersFromExternal, topicPath,
                Extractor::liveChannelExtractor);

        final DittoRuntimeException dittoRuntimeException = adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(jsonObject -> parseWithErrorRegistry(jsonObject, dittoHeaders, errorRegistry))
                .orElseThrow(() -> new JsonMissingFieldException(CommandResponse.JsonFields.PAYLOAD));

        return buildErrorResponse(topicPath, dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    @Override
    public Adaptable toAdaptable(final T errorResponse, final TopicPath.Channel channel) {

        final TopicPath topicPath = getTopicPath(errorResponse, channel);

        final Payload payload = Payload.newBuilder(errorResponse.getResourcePath())
                .withStatus(errorResponse.getHttpStatus())
                .withValue(errorResponse.toJson(errorResponse.getImplementedSchemaVersion())
                        .getValue(CommandResponse.JsonFields.PAYLOAD)
                        .orElse(JsonFactory.nullObject())) // only use the error payload
                .build();

        final DittoHeaders responseHeaders =
                ProtocolFactory.newHeadersWithJsonContentType(errorResponse.getDittoHeaders());

        return Adaptable.newBuilder(topicPath)
                .withPayload(payload)
                .withHeaders(DittoHeaders.of(headerTranslator.toExternalHeaders(responseHeaders)))
                .build();
    }

    @Override
    public Set<TopicPath.Action> getActions() {
        return Collections.emptySet();
    }

    @Override
    public TopicPath toTopicPath(final T t, final TopicPath.Channel channel) {
        return getTopicPath(t, channel);
    }

    /**
     * Implementations must provide a {@link TopicPath} for the given {@code errorResponse}.
     *
     * @param errorResponse the processed error response
     * @param channel the channel to used for determining the topic path.
     * @return the {@link TopicPath} used to processed the given {@code errorResponse}
     * @since 2.1.0
     */
    public abstract TopicPath getTopicPath(T errorResponse, TopicPath.Channel channel);

    /**
     * Implementations can build the {@link ErrorResponse} from the given parameters.
     *
     * @param topicPath the {@link TopicPath} used to build the error response
     * @param exception the {@link DittoRuntimeException} used to build the error response
     * @param dittoHeaders the {@link DittoHeaders} used to build the error response
     * @return the built error response
     */
    public abstract T buildErrorResponse(TopicPath topicPath, DittoRuntimeException exception,
            DittoHeaders dittoHeaders);

    protected static TopicPathBuildable addChannelToTopicPathBuilder(final TopicPathBuilder topicPathBuilder,
            final TopicPath.Channel channel) {
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
        return topicPathBuildable;
    }
}
