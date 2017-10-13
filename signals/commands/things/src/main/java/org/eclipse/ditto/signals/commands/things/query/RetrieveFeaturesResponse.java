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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveFeatures} command.
 */
@Immutable
public final class RetrieveFeaturesResponse extends AbstractCommandResponse<RetrieveFeaturesResponse>
        implements ThingQueryCommandResponse<RetrieveFeaturesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatures.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURES =
            JsonFactory.newJsonObjectFieldDefinition("features", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final Features features;

    private RetrieveFeaturesResponse(final String thingId, final Features features, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thing ID");
        this.features = features;
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command.
     *
     * @param thingId the Thing ID of the retrieved features.
     * @param features the retrieved Features.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeaturesResponse of(final String thingId, final Features features,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeaturesResponse(thingId, checkNotNull(features, "retrieved Features"), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command.
     *
     * @param thingId the Thing ID of the retrieved features.
     * @param jsonObject the retrieved Features.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static RetrieveFeaturesResponse of(final String thingId, @Nullable final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final Features features = (null != jsonObject)
                ? ThingsModelFactory.newFeatures(jsonObject)
                : ThingsModelFactory.nullFeatures();

        return of(thingId, features, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveFeaturesResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveFeaturesResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveFeaturesResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingQueryCommandResponse.JsonFields.JSON_THING_ID);
                    final JsonObject featuresJsonObject = jsonObject.getValueOrThrow(JSON_FEATURES);

                    final Features features = (null != featuresJsonObject)
                            ? ThingsModelFactory.newFeatures(featuresJsonObject)
                            : ThingsModelFactory.nullFeatures();

                    return of(thingId, features, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the retrieved Features.
     *
     * @return the retrieved Features.
     */
    public Features getFeatures() {
        return features;
    }

    @Override
    public JsonObject getEntity(final JsonSchemaVersion schemaVersion) {
        return features.toJson(schemaVersion);
    }

    @Override
    public RetrieveFeaturesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(thingId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveFeaturesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, features, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURES, getEntity(schemaVersion), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveFeaturesResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeaturesResponse that = (RetrieveFeaturesResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(features, that.features) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, features);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", features=" +
                features + "]";
    }

}
