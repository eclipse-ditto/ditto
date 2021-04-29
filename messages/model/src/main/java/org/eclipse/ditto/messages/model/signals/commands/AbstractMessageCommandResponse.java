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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;

/**
 * Base class for {@link MessageCommandResponse}s.
 *
 * @param <T> the type of the message's payload.
 * @param <C> the type of the AbstractMessageCommand.
 */
abstract class AbstractMessageCommandResponse<T, C extends AbstractMessageCommandResponse<T, C>>
        extends AbstractCommandResponse<C> implements MessageCommandResponse<T, C> {

    private final ThingId thingId;
    private final Message<T> message;

    /**
     * @since 2.0.0
     */
    AbstractMessageCommandResponse(final String type,
            final ThingId thingId,
            final Message<T> message,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(type, httpStatus, dittoHeaders);
        this.thingId = ConditionChecker.checkNotNull(thingId, "thingId");
        this.message = ConditionChecker.checkNotNull(message, "message");
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

        jsonObjectBuilder.set(MessageCommandResponse.JsonFields.JSON_THING_ID, getEntityId().toString(),
                predicate);

        final JsonObjectBuilder messageBuilder = JsonFactory.newObjectBuilder();
        final JsonObject messageHeadersObject = message.getHeaders().toJson();
        messageBuilder.set(MessageCommandResponse.JsonFields.JSON_MESSAGE_HEADERS, messageHeadersObject, predicate);

        MessagePayloadSerializer.serialize(message, messageBuilder, predicate);

        final JsonObject messageObject = messageBuilder.build();
        jsonObjectBuilder.set(MessageCommandResponse.JsonFields.JSON_MESSAGE, messageObject, predicate);
    }

    /**
     * Deserializes the {@link org.eclipse.ditto.messages.model.Message} from the JSON representation - the {@code rawPayload} is decoded with Base64.
     *
     * @param jsonObject the JsonObjectReader to use for reading the message
     * @return the Message
     */
    protected static Message<?> deserializeMessageFromJson(final JsonObject jsonObject) {
        return AbstractMessageCommand.deserializeMessageFromJson(jsonObject);
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
        return other instanceof AbstractMessageCommandResponse;
    }

    @Override
    public String toString() {
        return "thingId=" + thingId + ", message=" + message;
    }

    @Override
    public abstract C setDittoHeaders(final DittoHeaders dittoHeaders);

}
