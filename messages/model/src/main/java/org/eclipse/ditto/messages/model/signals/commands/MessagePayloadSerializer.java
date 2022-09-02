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
package org.eclipse.ditto.messages.model.signals.commands;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageBuilder;
import org.eclipse.ditto.messages.model.MessageHeaders;

/**
 * (De-)Serializes message payloads of {@code MessageCommand}s and {@code MessageCommandResponse}s.
 */
public class MessagePayloadSerializer {

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private MessagePayloadSerializer() {
    }

    static <T> void serialize(final Message<T> message, final JsonObjectBuilder messageBuilder,
            final Predicate<JsonField> predicate) {

        final Optional<ByteBuffer> rawPayloadOptional = message.getRawPayload();
        final Optional<T> payloadOptional = message.getPayload();
        final ContentType contentType = message.getContentType().map(ContentType::of).orElse(ContentType.of(""));
        final JsonValue payloadValue;
        if (rawPayloadOptional.isPresent() && !payloadOptional.filter(JsonValue.class::isInstance).isPresent()) {
            final ByteBuffer rawPayload = rawPayloadOptional.get();
            if (MessageDeserializer.shouldBeInterpretedAsTextOrJson(contentType)) {
                payloadValue =
                        interpretAsJsonValue(new String(rawPayload.array(), StandardCharsets.UTF_8), contentType);
            } else {
                final ByteBuffer base64Encoded = BASE64_ENCODER.encode(rawPayload);
                payloadValue = JsonFactory.newValue(new String(base64Encoded.array(), StandardCharsets.UTF_8));
            }
        } else if (payloadOptional.isPresent()) {
            final T payload = payloadOptional.get();
            payloadValue = payload instanceof JsonValue
                    ? (JsonValue) payload
                    : interpretAsJsonValue(payload.toString(), contentType);
        } else {
            payloadValue = null;
        }
        injectMessagePayload(messageBuilder, predicate, payloadValue, message.getHeaders());
    }

    private static JsonValue interpretAsJsonValue(final String payloadString, final ContentType contentType) {
        final boolean isJson = contentType.isJson();
        final JsonValue readJsonValue =
                isJson ? JsonFactory.readFrom(payloadString) : JsonFactory.newValue(payloadString);
        if (isJson && readJsonValue.isString()) {
            // for JSON string with JSON content type, do not just set it in the payload
            // because it will be impossible to distinguish it from plain text string. Rather encode it first.
            return JsonFactory.newValue(payloadString);
        } else {
            return readJsonValue;
        }
    }

    private static void injectMessagePayload(final JsonObjectBuilder messageBuilder,
            final Predicate<JsonField> predicate,
            @Nullable final JsonValue payloadValue,
            final MessageHeaders messageHeaders) {

        if (payloadValue != null) {
            MessageCommandSizeValidator.getInstance()
                    .ensureValidSize(payloadValue.formatAsString()::length, () -> messageHeaders);
            messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, payloadValue, predicate);
        } else {
            MessageCommandSizeValidator.getInstance().ensureValidSize(() -> 0, () -> messageHeaders);
        }
    }

    // also validate message size
    public static void deserialize(@Nullable final JsonValue payload, final MessageBuilder<Object> messageBuilder,
            final MessageHeaders messageHeaders) {

        final ContentType contentType = messageHeaders.getDittoContentType().orElse(ContentType.of(""));
        if (payload != null) {
            final boolean isJson = contentType.isJson();

            // JSON content type should maintain payload's JSON representation.
            // All other content types just read the content.
            final String payloadStr = isJson ? payload.toString() : payload.formatAsString();

            final byte[] payloadBytes = payloadStr.getBytes(StandardCharsets.UTF_8);
            MessageCommandSizeValidator.getInstance().ensureValidSize(() -> payloadBytes.length, () -> messageHeaders);

            if (isJson) {
                messageBuilder.payload(payload)
                        .rawPayload(ByteBuffer.wrap(payloadBytes));
            } else if (contentType.isText()) {
                messageBuilder.payload(payloadStr)
                        .rawPayload(ByteBuffer.wrap(payloadBytes));
            } else {
                final byte[] decodedBytes = tryToBase64Decode(payloadBytes);
                messageBuilder.payload(ByteBuffer.wrap(decodedBytes))
                        .rawPayload(ByteBuffer.wrap(decodedBytes));
            }
        } else {
            MessageCommandSizeValidator.getInstance().ensureValidSize(() -> 0, () -> messageHeaders);
        }
    }

    private static byte[] tryToBase64Decode(final byte[] inputBytes) {
        try {
            return BASE64_DECODER.decode(inputBytes);
        } catch (final IllegalArgumentException e) {
            // not base64-encoded; fallback to input bytes
            return inputBytes;
        }
    }

}
