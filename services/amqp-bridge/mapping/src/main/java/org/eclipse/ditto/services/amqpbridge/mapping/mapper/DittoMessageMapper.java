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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;

import com.google.common.base.Converter;

/**
 * A message mapper implementation for the ditto protocol.
 * Expects messages to contain a JSON serialized ditto protocol message.
 */
public class DittoMessageMapper extends MessageMapper {

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

    /**
     * Creates a new mapper with enabled content type check.
     */
    @SuppressWarnings("WeakerAccess")
    public DittoMessageMapper() {
        this(true);
    }

    /**
     * Creates a new mapper.
     * @param isContentTypeRequired if content type check should be performed prior mapping
     */
    public DittoMessageMapper(final boolean isContentTypeRequired) {
        super(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, isContentTypeRequired);
    }

    @Override
    public void configure(@Nonnull final MessageMapperConfiguration configuration) {
        configuration.findProperty(OPT_CONTENT_TYPE_REQUIRED).map(Boolean::valueOf)
                .ifPresent(this::setContentTypeRequired);
    }

    @Override
    protected Adaptable doForward(final InternalMessage message) {
        requireMatchingContentType(message);

        final Optional<String> payload = message.isTextMessage() ? message.getTextPayload()
                : message.getBytePayload().isPresent() ? message.getBytePayload()
                .map(ByteBuffer::array)
                .map(ba -> new String(ba, StandardCharsets.UTF_8))
                : Optional.empty();

        return payload.filter(s -> !s.isEmpty()).map(STRING_ADAPTABLE_CONVERTER::convert)
                .orElseThrow(() -> new IllegalArgumentException("Message contains no valid payload"));
    }

    @Override
    protected InternalMessage doBackward(final Adaptable adaptable) {
        final Map<String, String> headers = new LinkedHashMap<>(adaptable.getHeaders().orElse(DittoHeaders.empty()));
        headers.put(MessageMapper.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        return new InternalMessage.Builder(headers)
                .withText(STRING_ADAPTABLE_CONVERTER.reverse().convert(adaptable))
                .build();
    }


}
