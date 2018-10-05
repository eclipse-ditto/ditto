/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.messages;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageHeaders;

/**
 * (De-)Serializes message payloads of {@code MessageCommand}s and {@code MessageCommandResponse}s.
 */
class MessagePayloadSerializer {

    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_JSON = "application/json";

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private MessagePayloadSerializer() {
    }

    static <T> void serialize(final Message<T> message, final JsonObjectBuilder messageBuilder,
            final Predicate<JsonField> predicate) {

        final Optional<ByteBuffer> rawPayloadOptional = message.getRawPayload();
        final Optional<T> payloadOptional = message.getPayload();
        if (rawPayloadOptional.isPresent() && !payloadOptional.filter(p -> p instanceof JsonValue).isPresent()) {
            final ByteBuffer rawPayload = rawPayloadOptional.get();
            final String encodedString;
            if (shouldBeInterpretedAsText(message.getContentType().orElse(""))) {
                encodedString = new String(rawPayload.array());
            } else {
                final ByteBuffer base64Encoded = BASE64_ENCODER.encode(rawPayload);
                encodedString = new String(base64Encoded.array(), StandardCharsets.UTF_8);
            }

            injectMessagePayload(messageBuilder, predicate, encodedString, message.getHeaders());
        } else if (payloadOptional.isPresent()) {
            final T payload = payloadOptional.get();
            if (payload instanceof JsonValue) {
                MessageCommandSizeValidator.getInstance().ensureValidSize(() ->
                                ((JsonValue) payload).toString().length(), message::getHeaders);

                messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, (JsonValue) payload, predicate);
            } else {
                injectMessagePayload(messageBuilder, predicate, payload.toString(), message.getHeaders());
            }
        }
    }

    private static void injectMessagePayload(final JsonObjectBuilder messageBuilder,
            final Predicate<JsonField> predicate, final String encodedString, final MessageHeaders messageHeaders) {
        MessageCommandSizeValidator.getInstance().ensureValidSize(encodedString::length, () -> messageHeaders);
        messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, JsonValue.of(encodedString), predicate);
    }

    static void deserialize(final Optional<JsonValue> messagePayloadOptional,
            final MessageBuilder messageBuilder, final MessageHeaders messageHeaders) {

        final String contentType = messageHeaders.getContentType().orElse("");
        if (messagePayloadOptional.isPresent()) {
            final JsonValue payload = messagePayloadOptional.get();
            if (shouldBeInterpretedAsText(contentType)) {
                messageBuilder.payload(payload.isString() ? payload.asString() : payload);
            } else {
                final String payloadStr = payload.isString()
                        ? payload.asString()
                        : payload.toString();
                final byte[] payloadBytes = payloadStr.getBytes(StandardCharsets.UTF_8);

                MessageCommandSizeValidator.getInstance().ensureValidSize(() -> payloadBytes.length, () -> messageHeaders);
                messageBuilder.rawPayload(ByteBuffer.wrap(BASE64_DECODER.decode(payloadBytes)));
            }
        }
    }

    private static boolean shouldBeInterpretedAsText(final String contentType) {
        return contentType.startsWith(TEXT_PLAIN) || contentType.startsWith(APPLICATION_JSON);
    }
}
