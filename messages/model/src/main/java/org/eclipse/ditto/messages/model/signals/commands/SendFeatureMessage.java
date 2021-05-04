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
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.messages.model.FeatureIdInvalidException;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;

/**
 * Command to send the message to a specific Feature of a Thing.
 *
 * @param <T> the type of the message's payload.
 */
@Immutable
@JsonParsableCommand(typePrefix = MessageCommand.TYPE_PREFIX, name = SendFeatureMessage.NAME)
public final class SendFeatureMessage<T> extends AbstractMessageCommand<T, SendFeatureMessage<T>>
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
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final String featureId;

    private SendFeatureMessage(final ThingId thingId,
            final String featureId,
            final Message<T> message,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, message, dittoHeaders);
        this.featureId = checkNotNull(featureId, "featureId");
        validateMessageFeatureId(featureId, message, dittoHeaders);
    }

    private static void validateMessageFeatureId(final String expectedFeatureId, final Message<?> message,
            final DittoHeaders dittoHeaders) {

        final Optional<String> messageFeatureIdOptional = message.getFeatureId();
        if (!messageFeatureIdOptional.isPresent()) {
            final String msgPattern = "The Message did not contain a feature ID at all! Expected was feature ID <{0}>.";
            throw FeatureIdInvalidException.newBuilder()
                    .message(MessageFormat.format(msgPattern, expectedFeatureId))
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        final String messageFeatureId = messageFeatureIdOptional.get();
        if (!messageFeatureId.equals(expectedFeatureId)) {
            final String msgPattern = "The Message contained feature ID <{0}>. Expected was feature ID <{1}>.";
            throw FeatureIdInvalidException.newBuilder()
                    .message(MessageFormat.format(msgPattern, messageFeatureId, expectedFeatureId))
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
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
    public static <T> SendFeatureMessage<T> of(final ThingId thingId,
            final String featureId,
            final Message<T> message,
            final DittoHeaders dittoHeaders) {

        return new SendFeatureMessage<>(thingId, featureId, message, dittoHeaders);
    }

    /**
     * Creates a new {@code SendFeatureMessage} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendFeatureMessage is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the parsed {@code jsonString} did not contain any of
     * <ul>
     *     <li>{@link MessageCommand.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID},</li>
     *     <li>{@link MessageCommand.JsonFields#JSON_MESSAGE}</li>
     * </ul>
     */
    public static SendFeatureMessage<?> fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendFeatureMessage} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendFeatureMessage is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain any of
     * <ul>
     *     <li>{@link MessageCommand.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID},</li>
     *     <li>{@link MessageCommand.JsonFields#JSON_MESSAGE}</li>
     * </ul>
     */
    public static SendFeatureMessage<?> fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SendFeatureMessage<?>>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(MessageCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final String featureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
            final Message<?> message = deserializeMessageFromJson(jsonObject);

            return of(thingId, featureId, message, dittoHeaders);
        });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public SendFeatureMessage<T> setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), featureId, getMessage(), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, predicate);

        jsonObjectBuilder.remove(MessageCommand.JsonFields.JSON_THING_ID);
        final JsonObject superBuild = jsonObjectBuilder.build();
        jsonObjectBuilder.removeAll();
        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_THING_ID, getEntityId().toString());
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
