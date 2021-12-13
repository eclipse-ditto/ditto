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
package org.eclipse.ditto.things.model.signals.commands.query;

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
 * Response to a {@link RetrieveFeatureProperty} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveFeaturePropertyResponse.TYPE)
public final class RetrieveFeaturePropertyResponse extends AbstractCommandResponse<RetrieveFeaturePropertyResponse>
        implements ThingQueryCommandResponse<RetrieveFeaturePropertyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureProperty.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_PROPERTY =
            JsonFieldDefinition.ofString("property", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_VALUE =
            JsonFieldDefinition.ofJsonValue("value", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveFeaturePropertyResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                JsonPointer.of(jsonObject.getValueOrThrow(JSON_PROPERTY)),
                                jsonObject.getValueOrThrow(JSON_VALUE),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    private final JsonPointer propertyPointer;
    private final JsonValue propertyValue;

    private RetrieveFeaturePropertyResponse(final ThingId thingId,
            final String featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = checkNotNull(featureId, "featureId");
        this.propertyPointer = checkPropertyPointer(propertyPointer);
        this.propertyValue = checkNotNull(propertyValue, "propertyValue");
    }

    private static JsonPointer checkPropertyPointer(final JsonPointer propertyPointer) {
        checkNotNull(propertyPointer, "propertyPointer");
        return ThingsModelFactory.validateFeaturePropertyPointer(propertyPointer);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperty} command.
     *
     * @param thingId the Thing ID of the retrieved feature property.
     * @param featureId the identifier of the Feature whose Property was retrieved.
     * @param featurePropertyPointer the retrieved FeatureProperty JSON pointer.
     * @param featurePropertyValue the retrieved FeatureProperty value.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code featurePropertyPointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveFeaturePropertyResponse of(final ThingId thingId,
            final String featureId,
            final JsonPointer featurePropertyPointer,
            final JsonValue featurePropertyValue,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, featurePropertyPointer, featurePropertyValue, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveFeaturePropertyResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature property belong to.
     * @param featureId the identifier of the feature whose property was retrieved.
     * @param propertyPointer the retrieved feature property JSON pointer.
     * @param propertyValue the retrieved feature property value.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveFeaturePropertyResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code RetrieveFeaturePropertyResponse}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code propertyPointer} is invalid.
     * @since 2.3.0
     */
    public static RetrieveFeaturePropertyResponse newInstance(final ThingId thingId,
            final String featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeaturePropertyResponse(thingId,
                featureId,
                propertyPointer,
                propertyValue,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveFeaturePropertyResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperty} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveFeaturePropertyResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperty} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveFeaturePropertyResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the identifier of the {@code Feature} whose properties were modified.
     *
     * @return the identifier.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the JSON pointer of the Property to retrieve.
     *
     * @return the JSON pointer of the Property to retrieve.
     */
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    /**
     * Returns the retrieved FeatureProperty.
     *
     * @return the retrieved FeatureProperty.
     */
    public JsonValue getPropertyValue() {
        return propertyValue;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return propertyValue;
    }

    @Override
    public RetrieveFeaturePropertyResponse setEntity(final JsonValue entity) {
        return newInstance(thingId,
                featureId,
                propertyPointer,
                checkNotNull(entity, "entity"),
                getHttpStatus(),
                getDittoHeaders());
    }

    @Override
    public RetrieveFeaturePropertyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, featureId, propertyPointer, propertyValue, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features/" + featureId + "/properties" + propertyPointer);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_PROPERTY, propertyPointer.toString(), predicate);
        jsonObjectBuilder.set(JSON_VALUE, propertyValue, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeaturePropertyResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeaturePropertyResponse that = (RetrieveFeaturePropertyResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(propertyPointer, that.propertyPointer) &&
                Objects.equals(propertyValue, that.propertyValue) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, propertyPointer, propertyValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", featureId=" +
                featureId + ", propertyPointer=" + propertyPointer + ", propertyValue=" + propertyValue + "]";
    }

}
