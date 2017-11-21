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
package org.eclipse.ditto.signals.commands.messages;

import static java.util.Objects.requireNonNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Base class for {@link MessageCommandResponse}s.
 *
 * @param <T> the type of the message's payload.
 * @param <C> the type of the AbstractMessageCommand.
 */
abstract class AbstractMessageCommandResponse<T, C extends AbstractMessageCommandResponse>
        extends AbstractCommandResponse<C> implements MessageCommandResponse<T, C> {

    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_JSON = "application/json";

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final String thingId;
    private final Message<T> message;

    AbstractMessageCommandResponse(final String type,
            final String thingId,
            final Message<T> message,
            final HttpStatusCode httpStatusCode,
            final DittoHeaders dittoHeaders) {

        super(type, httpStatusCode, dittoHeaders);
        this.thingId = requireNonNull(thingId, "The thingId cannot be null.");
        this.message = requireNonNull(message, "The message cannot be null.");
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public Message<T> getMessage() {
        return message;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(MessageCommandResponse.JsonFields.JSON_THING_ID, getThingId(), predicate);

        final JsonObjectBuilder messageBuilder = JsonFactory.newObjectBuilder();
        final JsonObject messageHeadersObject = message.getHeaders().toJson();
        messageBuilder.set(MessageCommandResponse.JsonFields.JSON_MESSAGE_HEADERS, messageHeadersObject, predicate);

        final Optional<ByteBuffer> rawPayloadOptional = message.getRawPayload();
        final Optional<T> payloadOptional = message.getPayload();
        if (rawPayloadOptional.isPresent() && !payloadOptional.filter(p -> p instanceof JsonValue).isPresent()) {
            final String encodedString;
            if (shouldBeInterpretedAsText(message.getContentType().orElse(""))) {
                encodedString = message.getRawPayload()
                        .map(payload -> new String(payload.array()))
                        .orElse("");
            } else {
                encodedString = message.getRawPayload()
                        .map(BASE64_ENCODER::encode)
                        .map(base64Encoded -> new String(base64Encoded.array(), StandardCharsets.UTF_8))
                        .orElse("");
            }

            messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, JsonValue.of(encodedString), predicate);
        } else if (payloadOptional.isPresent()) {
            final T payload = payloadOptional.get();
            if (payload instanceof JsonValue) {
                messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, (JsonValue) payload, predicate);
            } else {
                messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, JsonValue.of(payload.toString()),
                        predicate);
            }
        }

        final JsonObject messageObject = messageBuilder.build();
        jsonObjectBuilder.set(MessageCommandResponse.JsonFields.JSON_MESSAGE, messageObject, predicate);
    }

    /**
     * Deserializes the {@link Message} from the JSON representation - the {@code rawPayload} is decoded with Base64.
     *
     * @param <T> the type of the message's payload.
     * @param jsonObject the JsonObjectReader to use for reading the message
     * @return the Message
     */
    protected static <T> Message<T> deserializeMessageFromJson(final JsonObject jsonObject) {
        final JsonObject messageObject = jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_MESSAGE);
        final JsonObject messageHeadersObject =
                messageObject.getValue(MessageCommandResponse.JsonFields.JSON_MESSAGE_HEADERS)
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                                .fieldName(
                                        MessageCommandResponse.JsonFields.JSON_MESSAGE_HEADERS.getPointer().toString())
                                .build());

        final MessageHeaders messageHeaders = MessageHeaders.of(messageHeadersObject);
        final MessageBuilder<T> messageBuilder = Message.<T>newBuilder(messageHeaders);

        final Optional<JsonValue> messagePayloadValue =
                messageObject.getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD);
        if (messagePayloadValue.isPresent()) {
            final JsonValue payload = messagePayloadValue.get();
            final String payloadStr = payload.isString()
                    ? payload.asString()
                    : payload.toString();
            final byte[] payloadBytes = payloadStr.getBytes(StandardCharsets.UTF_8);
            if (shouldBeInterpretedAsText(messageHeaders.getContentType().orElse(""))) {
                messageBuilder.rawPayload(ByteBuffer.wrap(payloadBytes));
            } else {
                messageBuilder.rawPayload(ByteBuffer.wrap(BASE64_DECODER.decode(payloadBytes)));
            }
        }
        return messageBuilder.build();
    }

    private static boolean shouldBeInterpretedAsText(final String contentType) {
        return contentType.startsWith(TEXT_PLAIN) || contentType.startsWith(APPLICATION_JSON);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, message);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AbstractMessageCommandResponse<?, ?> other = (AbstractMessageCommandResponse<?, ?>) obj;
        return other.canEqual(this) && Objects.equals(thingId, other.thingId)
                && Objects.equals(message, other.message)
                && super.equals(other);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AbstractMessageCommandResponse);
    }

    @Override
    public String toString() {
        return "thingId=" + thingId + ", message=" + message;
    }

    @Override
    public abstract C setDittoHeaders(final DittoHeaders dittoHeaders);
}
