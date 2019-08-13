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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;

/**
 * Base class for {@link MessageCommand}s.
 *
 * @param <T> the type of the message's payload.
 * @param <C> the type of the AbstractMessageCommand.
 */
abstract class AbstractMessageCommand<T, C extends AbstractMessageCommand> extends AbstractCommand<C>
        implements MessageCommand<T, C> {

    private final ThingId thingId;
    private final Message<T> message;

    AbstractMessageCommand(final String type,
            final ThingId thingId,
            final Message<T> message,
            final DittoHeaders dittoHeaders) {

        super(type, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.message = checkNotNull(message, "message");

        validateThingId(message.getThingEntityId(), dittoHeaders);
    }

    private void validateThingId(final ThingId thingIdFromMessage, final DittoHeaders dittoHeaders) {
        if (!thingId.equals(thingIdFromMessage)) {
            final String descTemplate = "It does not match the 'thingId' from the Message the command" +
                    " transports (<{0}>). Please ensure that they are equal.";
            throw ThingIdInvalidException.newBuilder(String.valueOf(thingId))
                    .description(MessageFormat.format(descTemplate, thingIdFromMessage))
                    .dittoHeaders(dittoHeaders).build();
        }
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public Message<T> getMessage() {
        return message;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_THING_ID, getThingEntityId().toString(), predicate);

        final JsonObjectBuilder messageBuilder = JsonFactory.newObjectBuilder();
        final JsonObject headersObject = message.getHeaders().toJson();
        messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS, headersObject, predicate);

        MessagePayloadSerializer.serialize(message, messageBuilder, predicate);

        final JsonObject messageObject = messageBuilder.build();
        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE, messageObject, predicate);
    }

    /**
     * Deserializes the {@link Message} from the JSON representation - the {@code rawPayload} is decoded with Base64.
     *
     * @param <T> the type of the message's payload.
     * @param jsonObject the JsonObjectReader to use for reading the message.
     * @return the Message.
     */
    protected static <T> Message<T> deserializeMessageFromJson(final JsonObject jsonObject) {
        final JsonObject messageObject = jsonObject.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE);
        final JsonObject messageHeadersObject =
                messageObject.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS);
        final Optional<JsonValue> messagePayloadOptional =
                messageObject.getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD);

        final MessageHeaders messageHeaders = MessageHeaders.of(messageHeadersObject);
        final MessageBuilder<T> messageBuilder = Message.newBuilder(messageHeaders);
        MessagePayloadSerializer.deserialize(messagePayloadOptional, messageBuilder,
                messageHeaders);
        return messageBuilder.build();
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
        final AbstractMessageCommand<?, ?> other = (AbstractMessageCommand<?, ?>) obj;
        return other.canEqual(this) && Objects.equals(thingId, other.thingId)
                && Objects.equals(message, other.message)
                && super.equals(other);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractMessageCommand;
    }

    @Override
    public String toString() {
        return "thingId=" + thingId + ", message=" + message;
    }

    @Override
    public abstract C setDittoHeaders(final DittoHeaders dittoHeaders);

}
