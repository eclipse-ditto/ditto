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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveFeatureProperty} command.
 */
@Immutable
public final class RetrieveFeaturePropertyResponse extends AbstractCommandResponse<RetrieveFeaturePropertyResponse>
        implements ThingQueryCommandResponse<RetrieveFeaturePropertyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureProperty.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_PROPERTY =
            JsonFactory.newStringFieldDefinition("property", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_VALUE =
            JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final String featureId;
    private final JsonPointer propertyPointer;
    private final JsonValue propertyValue;

    private RetrieveFeaturePropertyResponse(final String thingId, final String featureId,
            final JsonPointer propertyPointer, final JsonValue propertyValue, final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thing ID");
        this.featureId = checkNotNull(featureId, "Feature ID");
        this.propertyPointer = checkNotNull(propertyPointer, "Property Pointer");
        this.propertyValue = checkNotNull(propertyValue, "Property Value");
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
     */
    public static RetrieveFeaturePropertyResponse of(final String thingId, final String featureId,
            final JsonPointer featurePropertyPointer,
            final JsonValue featurePropertyValue, final DittoHeaders dittoHeaders) {
        return new RetrieveFeaturePropertyResponse(thingId, featureId, featurePropertyPointer, featurePropertyValue,
                HttpStatusCode.OK, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperty} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveFeaturePropertyResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperty} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveFeaturePropertyResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<RetrieveFeaturePropertyResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingQueryCommandResponse.JsonFields.JSON_THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                    final String extractedPointerString = jsonObject.getValueOrThrow(JSON_PROPERTY);
                    final JsonPointer extractedPointer = JsonFactory.newPointer(extractedPointerString);
                    final JsonValue extractedFeatureProperty = jsonObject.getValueOrThrow(JSON_VALUE);

                    return of(thingId, extractedFeatureId, extractedPointer, extractedFeatureProperty, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
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
        checkNotNull(entity, "entity");
        return of(thingId, featureId, propertyPointer, entity, getDittoHeaders());
    }

    @Override
    public RetrieveFeaturePropertyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, propertyPointer, propertyValue, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/properties" + propertyPointer;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
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
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(featureId, that.featureId) && Objects.equals(propertyPointer, that.propertyPointer)
                && Objects.equals(propertyValue, that.propertyValue) && super.equals(o);
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
