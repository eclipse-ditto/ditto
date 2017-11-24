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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command to send the message to a specific Feature of a Thing.
 *
 * @param <T> the type of the message's payload.
 */
@Immutable
public final class SendFeatureMessage<T> extends AbstractMessageCommand<T, SendFeatureMessage>
        implements WithFeatureId {

    /**
     * The name of the {@code Message} wrapped by this {@code MessageCommand}.
     */
    public static final String NAME = "featureMessage";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String featureId;

    private SendFeatureMessage(final String thingId,
            final String featureId,
            final Message<T> message,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, message, dittoHeaders);
        this.featureId = requireNonNull(featureId, "The featureId cannot be null.");
    }

    @Override
    public SendFeatureMessage setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), featureId, getMessage(), dittoHeaders);
    }

    /**
     * Creates a new instance of {@code SendFeatureMessage}.
     *
     * @param thingId the ID of the Thing to which the Feature belongs
     * @param featureId the ID of the Feature to send the message to
     * @param message the message to send to the Feature
     * @param dittoHeaders the DittoHeaders of this message.
     * @param <T> the type of the message's payload.
     * @return new instance of {@code SendFeatureMessage}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <T> SendFeatureMessage<T> of(final String thingId, final String featureId, final Message<T> message,
            final DittoHeaders dittoHeaders) {
        return new SendFeatureMessage<>(thingId, featureId, message, dittoHeaders);
    }

    /**
     * Creates a new {@code SendFeatureMessage} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendFeatureMessage is to be created.
     * @param dittoHeaders the headers.
     * @param <T> the type of the message's payload
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static <T> SendFeatureMessage<T> fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendFeatureMessage} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendFeatureMessage is to be created.
     * @param dittoHeaders the headers.
     * @param <T> the type of the message's payload
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static <T> SendFeatureMessage<T> fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SendFeatureMessage<T>>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(MessageCommand.JsonFields.JSON_THING_ID);
            final String featureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
            final Message<T> message = deserializeMessageFromJson(jsonObject);

            return of(thingId, featureId, message, dittoHeaders);
        });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, predicate);

        jsonObjectBuilder.remove(MessageCommand.JsonFields.JSON_THING_ID);
        final JsonObject superBuild = jsonObjectBuilder.build();
        jsonObjectBuilder.removeAll();
        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_THING_ID, getThingId());
        jsonObjectBuilder.set(JSON_FEATURE_ID, getFeatureId());
        jsonObjectBuilder.setAll(superBuild);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(featureId);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SendFeatureMessage<?> that = (SendFeatureMessage<?>) obj;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SendFeatureMessage;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId + "]";
    }

}
