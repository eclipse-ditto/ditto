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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveFeatureDesiredProperty} command.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveFeatureDesiredPropertyResponse.TYPE)
public final class RetrieveFeatureDesiredPropertyResponse
        extends AbstractCommandResponse<RetrieveFeatureDesiredPropertyResponse>
        implements ThingQueryCommandResponse<RetrieveFeatureDesiredPropertyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureDesiredProperty.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_DESIRED_PROPERTY =
            JsonFieldDefinition.ofString("desiredProperty", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_DESIRED_VALUE =
            JsonFieldDefinition.ofJsonValue("desiredValue", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveFeatureDesiredPropertyResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                JsonPointer.of(jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY)),
                                jsonObject.getValueOrThrow(JSON_DESIRED_VALUE),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    private final JsonPointer desiredPropertyPointer;
    private final JsonValue desiredPropertyValue;

    private RetrieveFeatureDesiredPropertyResponse(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final JsonValue desiredPropertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = argumentNotEmpty(featureId, "featureId").toString();
        this.desiredPropertyPointer = checkDesiredPropertyPointer(desiredPropertyPointer);
        this.desiredPropertyValue = checkNotNull(desiredPropertyValue, "desiredPropertyValue");
    }

    private static JsonPointer checkDesiredPropertyPointer(final JsonPointer desiredPropertyPointer) {
        checkNotNull(desiredPropertyPointer, "desiredPropertyPointer");
        return ThingsModelFactory.validateFeaturePropertyPointer(desiredPropertyPointer);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDesiredProperty} command.
     *
     * @param thingId the Thing ID of the retrieved desired property.
     * @param featureId the identifier of the Feature whose desired property was retrieved.
     * @param desiredPropertyPointer the retrieved desired property JSON pointer.
     * @param desiredPropertyValue the retrieved desired property value.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code desiredPropertyPointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveFeatureDesiredPropertyResponse of(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final JsonValue desiredPropertyValue,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, desiredPropertyPointer, desiredPropertyValue, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveFeatureDesiredPropertyResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature desired property belong to.
     * @param featureId the identifier of the feature whose desired property was retrieved.
     * @param propertyPointer the retrieved feature desired property JSON pointer.
     * @param propertyValue the retrieved feature desired property value.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveFeatureDesiredPropertyResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code desiredPropertyPointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 2.3.0
     */
    public static RetrieveFeatureDesiredPropertyResponse newInstance(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeatureDesiredPropertyResponse(thingId,
                featureId,
                propertyPointer,
                propertyValue,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveFeatureDesiredPropertyResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDesiredProperty} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of the desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveFeatureDesiredPropertyResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDesiredProperty} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of the desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveFeatureDesiredPropertyResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the identifier of the {@code Feature} whose desired properties were modified.
     *
     * @return the identifier.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the JSON pointer of the desired property to retrieve.
     *
     * @return the JSON pointer of the desired property to retrieve.
     */
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    /**
     * Returns the retrieved desired property.
     *
     * @return the retrieved desired property.
     */
    public JsonValue getDesiredPropertyValue() {
        return desiredPropertyValue;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return desiredPropertyValue;
    }

    @Override
    public RetrieveFeatureDesiredPropertyResponse setEntity(final JsonValue entity) {
        return newInstance(thingId,
                featureId,
                desiredPropertyPointer,
                checkNotNull(entity, "entity"),
                getHttpStatus(),
                getDittoHeaders());
    }

    @Override
    public RetrieveFeatureDesiredPropertyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId,
                featureId,
                desiredPropertyPointer,
                desiredPropertyValue,
                getHttpStatus(),
                dittoHeaders);
    }

    /**
     * RetrieveFeatureDesiredPropertyResponse is only available in JsonSchemaVersion V_2.
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
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTY, desiredPropertyPointer.toString(), predicate);
        jsonObjectBuilder.set(JSON_DESIRED_VALUE, desiredPropertyValue, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeatureDesiredPropertyResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeatureDesiredPropertyResponse that = (RetrieveFeatureDesiredPropertyResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(desiredPropertyPointer, that.desiredPropertyPointer) &&
                Objects.equals(desiredPropertyValue, that.desiredPropertyValue) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, desiredPropertyPointer, desiredPropertyValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId
                + ", desiredPropertyPointer=" + desiredPropertyPointer
                + ", desiredPropertyValue=" + desiredPropertyValue + "]";
    }

}
