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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;

/**
 * Base class for {@link MessageCommand}s.
 *
 * @param <T> the type of the message's payload.
 * @param <C> the type of the AbstractMessageCommand.
 */
abstract class AbstractMessageCommand<T, C extends AbstractMessageCommand<T, C>> extends AbstractCommand<C>
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

        validateThingId(message.getEntityId(), dittoHeaders);
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
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public Message<T> getMessage() {
        return message;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_THING_ID, getEntityId().toString(), predicate);

        final JsonObjectBuilder messageBuilder = JsonFactory.newObjectBuilder();
        final JsonObject headersObject = message.getHeaders().toJson();
        messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS, headersObject, predicate);

        MessagePayloadSerializer.serialize(message, messageBuilder, predicate);

        final JsonObject messageObject = messageBuilder.build();
        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE, messageObject, predicate);
    }

    /**
     * Deserializes the {@link org.eclipse.ditto.messages.model.Message} from the JSON representation of the Command- the {@code rawPayload} is decoded with Base64.
     *
     * @param jsonObject the JsonObjectReader to use for reading the message.
     * @return the Message.
     */
    protected static Message<?> deserializeMessageFromJson(final JsonObject jsonObject) {
        final JsonObject messageObject = jsonObject.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE);
        return MessageDeserializer.deserializeMessageFromJson(messageObject);
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
        return super.toString() + ", thingId=" + thingId + ", message=" + message;
    }

    @Override
    public abstract C setDittoHeaders(final DittoHeaders dittoHeaders);

}
