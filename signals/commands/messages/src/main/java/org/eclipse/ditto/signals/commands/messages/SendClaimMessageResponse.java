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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Command to send a Claim response {@link Message} <em>FROM</em> a Thing.
 *
 * @param <T> the type of the message's payload.
 */
public final class SendClaimMessageResponse<T> extends AbstractMessageCommandResponse<T, SendClaimMessageResponse> {

    /**
     * The name of the {@code Message} wrapped by this {@code MessageCommand}.
     */
    public static final String NAME = "claimResponseMessage";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private SendClaimMessageResponse(final String thingId,
            final Message<T> message,
            final HttpStatusCode responseStatusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, message, responseStatusCode, dittoHeaders);
    }

    @Override
    public SendClaimMessageResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), getMessage(), getStatusCode(), dittoHeaders);
    }

    /**
     * Creates a new instance of {@code SendClaimMessageResponse}.
     *
     * @param thingId the ID of the Thing to send the message from.
     * @param message the claim response message to send from the Thing.
     * @param responseStatusCode the optional status code of this response.
     * @param dittoHeaders the DittoHeaders of this message.
     * @param <T> the type of the message's payload.
     * @return new instance of {@code SendClaimMessageResponse}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <T> SendClaimMessageResponse<T> of(final String thingId,
            final Message<T> message,
            final HttpStatusCode responseStatusCode,
            final DittoHeaders dittoHeaders) {

        return new SendClaimMessageResponse<>(thingId, message, responseStatusCode, dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessageResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendClaimMessageResponse is to be created.
     * @param dittoHeaders the headers.
     * @param <T> the type of the message's payload
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static <T> SendClaimMessageResponse<T> fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessageResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendClaimMessageResponse is to be created.
     * @param dittoHeaders the headers.
     * @param <T> the type of the message's payload
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static <T> SendClaimMessageResponse<T> fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<SendClaimMessageResponse<T>>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final String thingId = jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_THING_ID);
                    final Message<T> message = deserializeMessageFromJson(jsonObject);

                    return of(thingId, message, statusCode, dittoHeaders);
                });
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SendClaimMessageResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
