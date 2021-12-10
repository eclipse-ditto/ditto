/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link DeleteFeatureDesiredProperty} command.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommandResponse(type = DeleteFeatureDesiredPropertyResponse.TYPE)
public final class DeleteFeatureDesiredPropertyResponse
        extends AbstractCommandResponse<DeleteFeatureDesiredPropertyResponse>
        implements ThingModifyCommandResponse<DeleteFeatureDesiredPropertyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeleteFeatureDesiredProperty.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_DESIRED_PROPERTY =
            JsonFieldDefinition.ofString("desiredProperty", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.NO_CONTENT;

    private static final CommandResponseJsonDeserializer<DeleteFeatureDesiredPropertyResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                JsonPointer.of(jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    private final JsonPointer desiredPropertyPointer;

    private DeleteFeatureDesiredPropertyResponse(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = ConditionChecker.checkArgument(checkNotNull(featureId, "featureId").toString(),
                featureIdArgument -> !featureIdArgument.trim().isEmpty(),
                () -> "The featureId must neither be empty nor blank.");
        this.desiredPropertyPointer = checkDesiredPropertyPointer(desiredPropertyPointer);
    }

    private static JsonPointer checkDesiredPropertyPointer(final JsonPointer desiredPropertyPointer) {
        checkNotNull(desiredPropertyPointer, "desiredPropertyPointer");
        return ThingsModelFactory.validateFeaturePropertyPointer(desiredPropertyPointer);
    }

    /**
     * Creates a response to a {@link DeleteFeatureDesiredProperty} command.
     *
     * @param thingId the Thing ID of the deleted feature desired property.
     * @param featureId the {@code Feature}'s ID whose desired property was deleted.
     * @param desiredPropertyPointer the JSON pointer of the deleted desired property.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code desiredPropertyPointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteFeatureDesiredPropertyResponse of(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, desiredPropertyPointer, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code DeleteFeatureDesiredPropertyResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing of which the desired feature property was deleted from.
     * @param featureId ID of the feature of which the desired property was deleted.
     * @param desiredPropertyPointer the JSON pointer of the deleted desired property.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code DeleteFeatureDesiredPropertyResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code featureId} is empty or blank or if {@code httpStatus} is not allowed
     * for a {@code DeleteFeatureDesiredPropertyResponse}.
     * @since 2.3.0
     */
    public static DeleteFeatureDesiredPropertyResponse newInstance(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new DeleteFeatureDesiredPropertyResponse(thingId,
                featureId,
                desiredPropertyPointer,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        DeleteFeatureDesiredPropertyResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteFeatureDesiredProperty} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteFeatureDesiredPropertyResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteFeatureDesiredProperty} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteFeatureDesiredPropertyResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the {@code Feature}'s ID whose desired property was deleted.
     *
     * @return the Feature's ID.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the JSON pointer of the desired roperty to delete.
     *
     * @return the JSON pointer of the desired property to delete.
     */
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    /**
     * DeleteFeatureDesiredPropertyResponse is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features/" + featureId + "/desiredProperties" + desiredPropertyPointer);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        final Predicate<JsonField> p = schemaVersion.and(predicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), p);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, p);
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTY, desiredPropertyPointer.toString(), p);
    }

    @Override
    public DeleteFeatureDesiredPropertyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, featureId, desiredPropertyPointer, getHttpStatus(), dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteFeatureDesiredPropertyResponse that = (DeleteFeatureDesiredPropertyResponse) o;
        return Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(desiredPropertyPointer, that.desiredPropertyPointer) &&
                that.canEqual(this) &&
                super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeleteFeatureDesiredPropertyResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, featureId, desiredPropertyPointer, super.hashCode());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId + ", desiredPropertyPointer=" + desiredPropertyPointer + "]";
    }

}
