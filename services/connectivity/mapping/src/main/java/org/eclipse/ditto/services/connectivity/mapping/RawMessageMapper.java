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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.messages.MessageFormatInvalidException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.commands.messages.MessageDeserializer;

import akka.http.javadsl.model.ContentTypes;

/**
 * A message mapper implementation to convert between raw message payload and external message payload.
 */
@PayloadMapper(alias = {"RawMessage"})
public final class RawMessageMapper extends AbstractMessageMapper {

    private static final String OUTGOING_CONTENT_TYPE_KEY = "outgoing-content-type";
    private static final String INCOMING_CONTENT_TYPE_KEY = "incoming-content-type";

    /**
     * Default outgoing content type is text/plain because binary requires base64 encoded string as payload.
     */
    private static final String DEFAULT_OUTGOING_CONTENT_TYPE = ContentTypes.TEXT_PLAIN_UTF8.toString();

    /**
     * Default incoming content type is binary.
     */
    private static final String DEFAULT_INCOMING_CONTENT_TYPE = ContentTypes.APPLICATION_OCTET_STREAM.toString();

    private String fallbackOutgoingContentType = DEFAULT_OUTGOING_CONTENT_TYPE;
    private String fallbackIncomingContentType = DEFAULT_INCOMING_CONTENT_TYPE;

    /**
     * The context representing this mapper
     */
    public static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(
            RawMessageMapper.class.getCanonicalName(),
            Map.of()
    );

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return List.of();
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        if (isMessageCommandOrResponse(adaptable)) {
            final ExternalMessageBuilder builder = ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                    .withInternalHeaders(adaptable.getDittoHeaders());
            adaptable.getPayload().getValue().ifPresent(payloadValue -> {
                final String contentType = adaptable.getDittoHeaders()
                        .getContentType()
                        .orElse(fallbackOutgoingContentType);
                if (isTextOrJson(contentType)) {
                    builder.withText(toOutgoingText(payloadValue));
                } else {
                    // binary payload only possible if payload is a base64-encoded string.
                    builder.withBytes(Optional.of(payloadValue)
                            .filter(JsonValue::isString)
                            .flatMap(value -> toOutgoingBinary(value.asString()))
                            .orElseThrow(() -> badContentType(contentType, adaptable.getDittoHeaders()))
                    );
                }
            });
            return List.of(builder.build());
        } else {
            return List.of();
        }
    }

    @Override
    public Map<String, String> getDefaultOptions() {
        return Map.of();
    }

    @Override
    protected void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        fallbackOutgoingContentType =
                configuration.findProperty(OUTGOING_CONTENT_TYPE_KEY).orElse(fallbackOutgoingContentType);
        fallbackIncomingContentType =
                configuration.findProperty(OUTGOING_CONTENT_TYPE_KEY).orElse(fallbackIncomingContentType);
    }

    private static boolean isTextOrJson(final String contentType) {
        return MessageDeserializer.shouldBeInterpretedAsText(contentType);
    }

    private static String toOutgoingText(final JsonValue value) {
        return value.isString() ? value.asString() : value.toString();
    }

    private static Optional<byte[]> toOutgoingBinary(final String base64Encoded) {
        try {
            return Optional.of(Base64.getDecoder().decode(base64Encoded));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static MessageFormatInvalidException badContentType(final String contentType, final DittoHeaders headers) {
        return MessageFormatInvalidException.newBuilder(JsonFactory.nullArray())
                .message(String.format(
                        "Expect payload of a message of content-type <%s> to be a base64 encoded string.",
                        contentType))
                .description("Please make sure the message has the correct content-type.")
                .dittoHeaders(headers)
                .build();
    }

    private static boolean isMessageCommandOrResponse(final Adaptable adaptable) {
        return adaptable.getTopicPath().getCriterion() == TopicPath.Criterion.MESSAGES;
    }
}
