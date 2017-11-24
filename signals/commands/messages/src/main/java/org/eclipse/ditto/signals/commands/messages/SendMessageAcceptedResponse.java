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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Command to send a response to a {@link Message}.
 */
@Immutable
public final class SendMessageAcceptedResponse
        extends AbstractMessageCommandResponse<Void, SendMessageAcceptedResponse> {

    /**
     * The name of the {@code Message} wrapped by this {@code MessageCommand}.
     */
    public static final String NAME = "acceptedResponseMessage";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private SendMessageAcceptedResponse(final String thingId, final MessageHeaders messageHeaders,
            final HttpStatusCode statusCode, final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, Message.<Void>newBuilder(messageHeaders).build(), statusCode,
                dittoHeaders);
    }

    /**
     * Returns a new {@code SendMessageAcceptedResponse} instance.
     *
     * @param thingId the ID of the Thing to send the message from.
     * @param dittoHeaders the command headers.
     * @return the new instance.
     */
    public static SendMessageAcceptedResponse newInstance(final String thingId, final MessageHeaders messageHeaders,
            final DittoHeaders dittoHeaders) {
        return newInstance(thingId, messageHeaders, HttpStatusCode.ACCEPTED, dittoHeaders);
    }

    /**
     * Returns a new {@code SendMessageAcceptedResponse} instance.
     *
     * @param thingId the ID of the Thing to send the message from.
     * @param statusCode the HttpStatusCode to use.
     * @param dittoHeaders the DittoHeaders.
     * @return the new instance.
     */
    public static SendMessageAcceptedResponse newInstance(final String thingId, final MessageHeaders messageHeaders,
            final HttpStatusCode statusCode, final DittoHeaders dittoHeaders) {

        return new SendMessageAcceptedResponse(thingId, messageHeaders, statusCode, dittoHeaders);
    }

    /**
     * Creates a new {@code SendMessageAcceptedResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendMessageAcceptedResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SendMessageAcceptedResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendMessageAcceptedResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendMessageAcceptedResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SendMessageAcceptedResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<SendMessageAcceptedResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final String thingId = jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_THING_ID);
                    final JsonObject jsonHeaders =
                            jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_MESSAGE)
                                    .getValueOrThrow(MessageCommandResponse.JsonFields.JSON_MESSAGE_HEADERS);
                    final MessageHeaders messageHeaders = MessageHeaders.of(jsonHeaders);

                    return newInstance(thingId, messageHeaders, statusCode, dittoHeaders);
                });
    }

    @Override
    public SendMessageAcceptedResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(getThingId(), getMessage().getHeaders(), getStatusCode(), dittoHeaders);
    }

    public Optional<String> getCorrelationId() {
        return getDittoHeaders().getCorrelationId();
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SendMessageAcceptedResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
