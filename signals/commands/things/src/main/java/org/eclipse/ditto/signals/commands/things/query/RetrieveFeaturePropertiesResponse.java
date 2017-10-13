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
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveFeatureProperties} command.
 */
@Immutable
public final class RetrieveFeaturePropertiesResponse extends AbstractCommandResponse<RetrieveFeaturePropertiesResponse>
        implements ThingQueryCommandResponse<RetrieveFeaturePropertiesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureProperties.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_PROPERTIES =
            JsonFactory.newJsonObjectFieldDefinition("properties", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final String featureId;
    private final FeatureProperties featureProperties;

    private RetrieveFeaturePropertiesResponse(final String thingId, final String featureId,
            final FeatureProperties featureProperties, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thing ID");
        this.featureId = checkNotNull(featureId, "Feature ID");
        this.featureProperties = checkNotNull(featureProperties, "FeatureProperties");
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command.
     *
     * @param thingId the Thing ID of the retrieved feature properties.
     * @param featureId the identifier of the Feature whose Properties were retrieved.
     * @param featureProperties the retrieved FeatureProperties.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeaturePropertiesResponse of(final String thingId, final String featureId,
            final FeatureProperties featureProperties, final DittoHeaders dittoHeaders) {
        return new RetrieveFeaturePropertiesResponse(thingId, featureId, featureProperties, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command.
     *
     * @param thingId the Thing ID of the retrieved feature properties.
     * @param featureId the identifier of the Feature whose Properties were retrieved.
     * @param jsonObject the retrieved FeatureProperties JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeaturePropertiesResponse of(final String thingId, final String featureId,
            @Nullable final JsonObject jsonObject, final DittoHeaders dittoHeaders) {

        final FeatureProperties featureProperties;
        if (jsonObject == null || jsonObject.isNull()) {
            featureProperties = ThingsModelFactory.nullFeatureProperties();
        } else {
            featureProperties = ThingsModelFactory.newFeatureProperties(jsonObject);
        }

        return of(thingId, featureId, featureProperties, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveFeaturePropertiesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveFeaturePropertiesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<RetrieveFeaturePropertiesResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingQueryCommandResponse.JsonFields.JSON_THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                    final JsonObject extractedFeatureProperties = jsonObject.getValueOrThrow(JSON_PROPERTIES);

                    return of(thingId, extractedFeatureId, extractedFeatureProperties, dittoHeaders);
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
     * Returns the retrieved FeatureProperties.
     *
     * @return the retrieved FeatureProperties.
     */
    public FeatureProperties getFeatureProperties() {
        return featureProperties;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return featureProperties.toJson(schemaVersion);
    }

    @Override
    public RetrieveFeaturePropertiesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(thingId, featureId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveFeaturePropertiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, featureProperties, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/properties";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_PROPERTIES, featureProperties, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeaturePropertiesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeaturePropertiesResponse that = (RetrieveFeaturePropertiesResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(featureId, that.featureId)
                && Objects.equals(featureProperties, that.featureProperties) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, featureProperties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId + ", featureProperties=" + featureProperties + "]";
    }

}
