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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyFeatures} command.
 */
@Immutable
public final class ModifyFeaturesResponse extends AbstractCommandResponse<ModifyFeaturesResponse> implements
        ThingModifyCommandResponse<ModifyFeaturesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatures.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURES =
            JsonFactory.newJsonObjectFieldDefinition("features", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final Features featuresCreated;

    private ModifyFeaturesResponse(final String thingId,
            final HttpStatusCode statusCode,
            final Features featuresCreated,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.featuresCreated = featuresCreated;
    }

    /**
     * Returns a new {@code ModifyFeaturesResponse} for a created Feature. This corresponds to the HTTP status code
     * {@link HttpStatusCode#CREATED}.
     *
     * @param thingId the Thing ID of the created features.
     * @param features the created Features.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Feature.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturesResponse created(final String thingId, final Features features,
            final DittoHeaders dittoHeaders) {

        checkNotNull(features, "created Features");
        return new ModifyFeaturesResponse(thingId, HttpStatusCode.CREATED, features, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeaturesResponse} for a modified Feature. This corresponds to the HTTP status code
     * {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified features.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Feature.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyFeaturesResponse modified(final String thingId, final DittoHeaders dittoHeaders) {
        return new ModifyFeaturesResponse(thingId, HttpStatusCode.NO_CONTENT, ThingsModelFactory.nullFeatures(),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatures} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeaturesResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyFeatures} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeaturesResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyFeaturesResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
                    final JsonObject featuresJsonObject = jsonObject.getValueOrThrow(JSON_FEATURES);

                    final Features extractedFeatures = (null != featuresJsonObject)
                            ? ThingsModelFactory.newFeatures(featuresJsonObject)
                            : ThingsModelFactory.nullFeatures();

                    return new ModifyFeaturesResponse(thingId, statusCode, extractedFeatures, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the created {@code Features}.
     *
     * @return the created Features.
     */
    public Optional<Features> getFeaturesCreated() {
        return Optional.of(featuresCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(featuresCreated.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURES, featuresCreated.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public ModifyFeaturesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (HttpStatusCode.CREATED == getStatusCode())
                ? created(thingId, featuresCreated, dittoHeaders)
                : modified(thingId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyFeaturesResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeaturesResponse that = (ModifyFeaturesResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) &&
                Objects.equals(featuresCreated, that.featuresCreated) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featuresCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", featuresCreated=" +
                featuresCreated + "]";
    }

}
