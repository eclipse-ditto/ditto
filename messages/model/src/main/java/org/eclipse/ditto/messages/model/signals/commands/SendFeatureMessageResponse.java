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

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.messages.model.FeatureIdInvalidException;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Command to send a response {@link org.eclipse.ditto.messages.model.Message} <em>FROM</em> a Feature answering to a
 * {@link SendFeatureMessage}.
 *
 * @param <T> the type of the message's payload.
 */
@JsonParsableCommandResponse(type = SendFeatureMessageResponse.TYPE)
public final class SendFeatureMessageResponse<T>
        extends AbstractMessageCommandResponse<T, SendFeatureMessageResponse<T>>
        implements WithFeatureId {

    /**
     * The name of the {@code Message} wrapped by this {@code MessageCommand}.
     */
    public static final String NAME = "featureResponseMessage";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final CommandResponseJsonDeserializer<SendFeatureMessageResponse<?>> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new SendFeatureMessageResponse<>(
                                ThingId.of(jsonObject.getValueOrThrow(MessageCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                deserializeMessageFromJson(jsonObject),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final String featureId;

    private SendFeatureMessageResponse(final ThingId thingId,
            final String featureId,
            final Message<T> message,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, message, httpStatus, dittoHeaders);
        this.featureId = ConditionChecker.checkNotNull(featureId, "featureId");
        validateMessageFeatureId(this.featureId, message, dittoHeaders);
    }

    @Override
    public SendFeatureMessageResponse<T> setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), getFeatureId(), getMessage(), getHttpStatus(), dittoHeaders);
    }

    /**
     * Creates a new instance of {@code SendFeatureMessageResponse}.
     *
     * @param thingId the ID of the Thing to send the message from.
     * @param featureId the ID of the Feature to send the message from.
     * @param message the response message to send from the Thing.
     * @param responseStatus the optional HTTP status of this response.
     * @param dittoHeaders the DittoHeaders of this message.
     * @param <T> the type of the message's payload.
     * @return new instance of {@code SendFeatureMessageResponse}.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 2.0.0
     */
    public static <T> SendFeatureMessageResponse<T> of(final ThingId thingId,
            final String featureId,
            final Message<T> message,
            final HttpStatus responseStatus,
            final DittoHeaders dittoHeaders) {

        return new SendFeatureMessageResponse<>(thingId, featureId, message, responseStatus, dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessageResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendClaimMessageResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SendFeatureMessageResponse<?> fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessageResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendClaimMessageResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SendFeatureMessageResponse<?> fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    private static void validateMessageFeatureId(final String expectedFeatureId,
            final Message<?> message,
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

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
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
        final SendFeatureMessageResponse<?> that = (SendFeatureMessageResponse<?>) obj;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SendFeatureMessageResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId + "]";
    }

}
