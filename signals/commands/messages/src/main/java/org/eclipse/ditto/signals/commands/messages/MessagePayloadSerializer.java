/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.messages;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

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
            if (MessageDeserializer.shouldBeInterpretedAsTextOrJson(message.getContentType().orElse(""))) {
                encodedString = new String(rawPayload.array());
            } else {
                final ByteBuffer base64Encoded = BASE64_ENCODER.encode(rawPayload);
                encodedString = new String(base64Encoded.array(), StandardCharsets.UTF_8);
            }

            injectMessagePayload(messageBuilder, predicate, encodedString, message.getHeaders());
        } else if (payloadOptional.isPresent()) {
            final T payload = payloadOptional.get();
            if (payload instanceof JsonValue) {
                final JsonValue payloadAsJsonValue = (JsonValue) payload;
                MessageCommandSizeValidator.getInstance().ensureValidSize(
                        payloadAsJsonValue::getUpperBoundForStringSize,
                        () -> payloadAsJsonValue.toString().length(),
                        message::getHeaders);

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

    // also validate message size
    static void deserialize(@Nullable final JsonValue payload, final MessageBuilder<Object> messageBuilder,
            final MessageHeaders messageHeaders) {

        final String contentType = messageHeaders.getContentType().orElse("");
        if (payload != null) {
            final String payloadStr = payload.formatAsString();
            final byte[] payloadBytes = payloadStr.getBytes(StandardCharsets.UTF_8);
            MessageCommandSizeValidator.getInstance().ensureValidSize(() -> payloadBytes.length, () -> messageHeaders);

            if (MessageDeserializer.shouldBeInterpretedAsText(contentType)) {
                messageBuilder.payload(payloadStr)
                        .rawPayload(ByteBuffer.wrap(payloadBytes));
            } else if (MessageDeserializer.shouldBeInterpretedAsJson(contentType)) {
                messageBuilder.payload(payload)
                        .rawPayload(ByteBuffer.wrap(payloadBytes));
            } else {
                final byte[] decodedBytes = BASE64_DECODER.decode(payloadBytes);
                messageBuilder.payload(ByteBuffer.wrap(decodedBytes))
                        .rawPayload(ByteBuffer.wrap(decodedBytes));
            }
        } else {
            MessageCommandSizeValidator.getInstance().ensureValidSize(() -> 0, () -> messageHeaders);
        }
    }

}
