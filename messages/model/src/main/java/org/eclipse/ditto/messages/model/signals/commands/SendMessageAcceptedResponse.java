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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Command to send a response to a {@link org.eclipse.ditto.messages.model.Message}.
 */
@Immutable
@JsonParsableCommandResponse(type = SendMessageAcceptedResponse.TYPE)
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

    private static final CommandResponseJsonDeserializer<SendMessageAcceptedResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final JsonObject jsonMessage =
                                jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_MESSAGE);

                        return new SendMessageAcceptedResponse(
                                ThingId.of(jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_THING_ID)),
                                MessageHeaders.of(jsonMessage.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_MESSAGE_HEADERS)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private SendMessageAcceptedResponse(final ThingId thingId,
            final MessageHeaders messageHeaders,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, Message.<Void>newBuilder(messageHeaders).build(), httpStatus, dittoHeaders);
    }

    /**
     * Returns a new {@code SendMessageAcceptedResponse} instance.
     *
     * @param thingId the ID of the Thing to send the message from.
     * @param dittoHeaders the command headers.
     * @return the new instance.
     */
    public static SendMessageAcceptedResponse newInstance(final ThingId thingId, final MessageHeaders messageHeaders,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, messageHeaders, HttpStatus.ACCEPTED, dittoHeaders);
    }

    /**
     * Returns a new {@code SendMessageAcceptedResponse} instance.
     *
     * @param thingId the ID of the Thing to send the message from.
     * @param httpStatus the HTTP status to use.
     * @param dittoHeaders the DittoHeaders.
     * @return the new instance.
     * @since 2.0.0
     */
    public static SendMessageAcceptedResponse newInstance(final ThingId thingId,
            final MessageHeaders messageHeaders,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new SendMessageAcceptedResponse(thingId, messageHeaders, httpStatus, dittoHeaders);
    }

    /**
     * Creates a new {@code SendMessageAcceptedResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendMessageAcceptedResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SendMessageAcceptedResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendMessageAcceptedResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendMessageAcceptedResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SendMessageAcceptedResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public SendMessageAcceptedResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(getEntityId(), getMessage().getHeaders(), getHttpStatus(), dittoHeaders);
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
