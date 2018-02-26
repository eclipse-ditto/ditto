/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;

import com.google.common.base.Converter;

/**
 * A message mapper implementation for the ditto protocol.
 * Expects messages to contain a JSON serialized ditto protocol message.
 */
public final class DittoMessageMapper implements MessageMapper {

    @Nullable
    private String contentType;

    /**
     * A static converter to map adaptables to JSON strings and vice versa;
     */
    private static final Converter<String, Adaptable> STRING_ADAPTABLE_CONVERTER = Converter.from(
            s -> {
                try {
                    //noinspection ConstantConditions (converter guarantees nonnull value)
                    return ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(s));
                } catch (Exception e) {
                    throw new IllegalArgumentException(String.format("Failed to map '%s'", s), e);
                }
            },
            a -> {
                try {
                    return ProtocolFactory.wrapAsJsonifiableAdaptable(a).toJsonString();
                } catch (Exception e) {
                    throw new IllegalArgumentException(String.format("Failed to map '%s'", a), e);
                }
            }
    );

    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    public void configure(final MessageMapperConfiguration configuration) {
        configuration.findContentType().ifPresent(s -> contentType = s);
    }

    @Override
    public Adaptable map(@Nullable final InternalMessage message) {
        if (Objects.isNull(message)) return null;

        final String payload = extractPayloadAsString(message);
        final Adaptable adaptable = STRING_ADAPTABLE_CONVERTER.convert(payload);
        checkNotNull(adaptable);

        DittoHeaders mergedHeaders = mergeHeaders(message, adaptable);
        return ProtocolFactory.newAdaptableBuilder(adaptable).withHeaders(mergedHeaders).build();
    }

    @Override
    public InternalMessage map(@Nullable final Adaptable adaptable) {
        if (Objects.isNull(adaptable)) return null;

        final InternalMessage.MessageType messageType = determineMessageType(adaptable);
        final Map<String, String> headers = new LinkedHashMap<>(adaptable.getHeaders().orElse(DittoHeaders.empty()));
        headers.put(AbstractMessageMapper.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        return InternalMessage.Builder.from(headers, messageType)
                .withText(STRING_ADAPTABLE_CONVERTER.reverse().convert(adaptable))
                .build();
    }

    private static String extractPayloadAsString(final InternalMessage message) {
        final Optional<String> payload;
        if (message.isTextMessage()) {
            payload = message.getTextPayload();
        } else if (message.isBytesMessage()) {
            payload = message.getBytePayload().map(ByteBuffer::array).map(ba -> new String(ba, StandardCharsets.UTF_8));
        } else {
            payload = Optional.empty();
        }

        return payload.filter(s -> !s.isEmpty()).orElseThrow(
                () -> new IllegalArgumentException("Failed to extract string payload of message: " + message));
    }

    /**
     * Merge message headers of message and adaptable. Adaptable headers do override message headers!
     *
     * @param message the message
     * @param adaptable the adaptable
     * @return the merged headers
     */
    private static DittoHeaders mergeHeaders(final InternalMessage message, final Adaptable adaptable) {
        final Map<String, String> headers = new HashMap<>(message.getHeaders());
        adaptable.getHeaders().ifPresent(headers::putAll);
        return DittoHeaders.of(headers);
    }

    private InternalMessage.MessageType determineMessageType(final @Nonnull Adaptable adaptable) {
        final TopicPath.Criterion criterion = adaptable.getTopicPath().getCriterion();
        switch (criterion) {
            case COMMANDS:
                if (adaptable.getPayload().getStatus().isPresent()) {
                    return InternalMessage.MessageType.RESPONSE;
                } else {
                    return InternalMessage.MessageType.COMMAND;
                }
            case EVENTS:
                return InternalMessage.MessageType.EVENT;
            case MESSAGES:
                return InternalMessage.MessageType.MESSAGE;
            case ERRORS:
                return InternalMessage.MessageType.ERRORS;
            default:
                final String errorMessage = MessageFormat.format("Cannot map '{0}' message. Only [{1}, {2}] allowed.",
                        criterion.getName(), TopicPath.Criterion.COMMANDS, TopicPath.Criterion.EVENTS);
                throw new IllegalArgumentException(errorMessage);
        }
    }
}
