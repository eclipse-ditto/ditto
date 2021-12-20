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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Command to send a Claim response {@link org.eclipse.ditto.messages.model.Message} <em>FROM</em> a Thing.
 *
 * @param <T> the type of the message's payload.
 */
@JsonParsableCommandResponse(type = SendClaimMessageResponse.TYPE)
public final class SendClaimMessageResponse<T> extends AbstractMessageCommandResponse<T, SendClaimMessageResponse<T>> {

    /**
     * The name of the {@code Message} wrapped by this {@code MessageCommand}.
     */
    public static final String NAME = "claimResponseMessage";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final CommandResponseJsonDeserializer<SendClaimMessageResponse<?>> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new SendClaimMessageResponse<>(
                                ThingId.of(jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_THING_ID)),
                                deserializeMessageFromJson(jsonObject),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private SendClaimMessageResponse(final ThingId thingId,
            final Message<T> message,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, message, httpStatus, dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessageResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendClaimMessageResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static SendClaimMessageResponse<?> fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessageResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendClaimMessageResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static SendClaimMessageResponse<?> fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public SendClaimMessageResponse<T> setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), getMessage(), getHttpStatus(), dittoHeaders);
    }

    /**
     * Creates a new instance of {@code SendClaimMessageResponse}.
     *
     * @param thingId the ID of the Thing to send the message from.
     * @param message the claim response message to send from the Thing.
     * @param responseHttpStatus the optional HTTP status of this response.
     * @param dittoHeaders the DittoHeaders of this message.
     * @param <T> the type of the message's payload.
     * @return new instance of {@code SendClaimMessageResponse}.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 2.0.0
     */
    public static <T> SendClaimMessageResponse<T> of(final ThingId thingId,
            final Message<T> message,
            final HttpStatus responseHttpStatus,
            final DittoHeaders dittoHeaders) {

        return new SendClaimMessageResponse<>(thingId, message, responseHttpStatus, dittoHeaders);
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
