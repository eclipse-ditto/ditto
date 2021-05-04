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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;

/**
 * Command to send the message to a specific Thing.
 *
 * @param <T> the type of the message's payload.
 */
@Immutable
@JsonParsableCommand(typePrefix = MessageCommand.TYPE_PREFIX, name = SendThingMessage.NAME)
public final class SendThingMessage<T> extends AbstractMessageCommand<T, SendThingMessage<T>> {

    /**
     * The name of the {@code Message} wrapped by this {@code MessageCommand}.
     */
    public static final String NAME = "thingMessage";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private SendThingMessage(final ThingId thingId, final Message<T> message, final DittoHeaders dittoHeaders) {
        super(TYPE, thingId, message, dittoHeaders);
    }

    @Override
    public SendThingMessage<T> setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), getMessage(), dittoHeaders);
    }

    /**
     * Creates a new instance of {@link SendThingMessage}.
     *
     * @param thingId the ID of the Thing to send the message to
     * @param message the message to send to the Thing
     * @param dittoHeaders the DittoHeaders of this message.
     * @param <T> the type of the message's payload.
     * @return new instance of {@link SendThingMessage}.
     * @throws NullPointerException if any arguments is {@code null}.
     */
    public static <T> SendThingMessage<T> of(final ThingId thingId, final Message<T> message,
            final DittoHeaders dittoHeaders) {
        return new SendThingMessage<>(thingId, message, dittoHeaders);
    }

    /**
     * Creates a new {@code SendThingMessage} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendThingMessage is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SendThingMessage<?> fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendThingMessage} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendThingMessage is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SendThingMessage<?> fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SendThingMessage<?>>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(MessageCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final Message<?> message = deserializeMessageFromJson(jsonObject);

            return of(thingId, message, dittoHeaders);
        });
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SendThingMessage;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
