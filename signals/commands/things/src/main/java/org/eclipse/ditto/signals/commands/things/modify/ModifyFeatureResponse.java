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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyFeature} command.
 */
@Immutable
public final class ModifyFeatureResponse extends AbstractCommandResponse<ModifyFeatureResponse> implements
        ThingModifyCommandResponse<ModifyFeatureResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeature.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE =
            JsonFactory.newJsonObjectFieldDefinition("feature", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final Feature featureCreated;
    private final HttpStatusCode statusCode;

    private ModifyFeatureResponse(final String thingId, final Feature featureCreated,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.featureCreated = featureCreated;
        this.statusCode = statusCode;
    }

    /**
     * Returns a new {@code ModifyFeatureResponse} for a created Feature. This corresponds to the HTTP status code
     * {@link HttpStatusCode#CREATED}.
     *
     * @param thingId the Thing ID of the created feature.
     * @param feature the created Feature.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Feature.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureResponse created(final String thingId, final Feature feature,
            final DittoHeaders dittoHeaders) {
        checkNotNull(feature, "created Feature");
        return new ModifyFeatureResponse(thingId, feature, HttpStatusCode.CREATED, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeatureResponse} for a modified Feature. This corresponds to the HTTP status code
     * {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified feature.
     * @param featureId the identifier of the modified Feature.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Feature.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureResponse modified(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        return new ModifyFeatureResponse(thingId, ThingsModelFactory.nullFeature(featureId), HttpStatusCode.NO_CONTENT,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeature} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeatureResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeature} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeatureResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyFeatureResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                    final JsonObject featureJsonObject = jsonObject.getValueOrThrow(JSON_FEATURE);

                    final Feature extractedFeature;
                    if (featureJsonObject == null || featureJsonObject.isNull()) {
                        extractedFeature = ThingsModelFactory.nullFeature(extractedFeatureId);
                    } else {
                        extractedFeature = ThingsModelFactory.newFeatureBuilder(featureJsonObject)
                                .useId(extractedFeatureId)
                                .build();
                    }

                    return new ModifyFeatureResponse(thingId, extractedFeature, statusCode, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the created {@code Feature}.
     *
     * @return the created Feature.
     */
    public Optional<Feature> getFeatureCreated() {
        return Optional.of(featureCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(featureCreated.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureCreated.getId();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureCreated.getId());
        jsonObjectBuilder.set(JSON_FEATURE, featureCreated.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public ModifyFeatureResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeatureResponse(thingId, featureCreated, statusCode, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeatureResponse that = (ModifyFeatureResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureCreated, that.featureCreated) &&
                statusCode == that.statusCode &&
                super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureCreated, statusCode);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", featureCreated=" + featureCreated +
                ", statusCode=" + statusCode +
                "]";
    }

}
