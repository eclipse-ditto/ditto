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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
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
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;

/**
 * Response to a {@link RetrieveFeatureDesiredProperties} command.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveFeatureDesiredPropertiesResponse.TYPE)
public final class RetrieveFeatureDesiredPropertiesResponse
        extends AbstractCommandResponse<RetrieveFeatureDesiredPropertiesResponse>
        implements ThingQueryCommandResponse<RetrieveFeatureDesiredPropertiesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureDesiredProperties.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_DESIRED_PROPERTIES =
            JsonFactory.newJsonObjectFieldDefinition("desiredProperties", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final String featureId;
    private final FeatureProperties desiredProperties;

    private RetrieveFeatureDesiredPropertiesResponse(final ThingId thingId,
            final CharSequence featureId,
            final FeatureProperties desiredProperties,
            final DittoHeaders dittoHeaders) {

        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = argumentNotEmpty(featureId, "featureId").toString();
        this.desiredProperties = checkNotNull(desiredProperties, "desiredProperties");
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDesiredProperties} command.
     *
     * @param thingId the Thing ID of the retrieved desired properties.
     * @param featureId the identifier of the Feature whose desired properties were retrieved.
     * @param desiredProperties the retrieved desired properties.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeatureDesiredPropertiesResponse of(final ThingId thingId,
            final CharSequence featureId,
            final FeatureProperties desiredProperties,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeatureDesiredPropertiesResponse(thingId, featureId, desiredProperties, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDesiredProperties} command.
     *
     * @param thingId the Thing ID of the retrieved desired properties.
     * @param featureId the identifier of the Feature whose desired properties were retrieved.
     * @param jsonObject the retrieved desired properties JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeatureDesiredPropertiesResponse of(final ThingId thingId,
            final CharSequence featureId,
            @Nullable final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        checkNotNull(jsonObject, "jsonObject");
        final FeatureProperties desiredProperties = ThingsModelFactory.newFeatureProperties(jsonObject);

        return of(thingId, featureId, desiredProperties, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDesiredProperties} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveFeatureDesiredPropertiesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDesiredProperties} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveFeatureDesiredPropertiesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<RetrieveFeatureDesiredPropertiesResponse>(TYPE, jsonObject)
                .deserialize(httpStatus -> of(
                        ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                        jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                        jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTIES), dittoHeaders));
    }

    @Override
    public ThingId getThingEntityId() {
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
     * Returns the retrieved desired properties.
     *
     * @return the retrieved desired properties.
     */
    public FeatureProperties getDesiredProperties() {
        return desiredProperties;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return desiredProperties.toJson(schemaVersion);
    }

    @Override
    public RetrieveFeatureDesiredPropertiesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(thingId, featureId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveFeatureDesiredPropertiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, desiredProperties, dittoHeaders);
    }

    /**
     * RetrieveFeatureDesiredPropertiesResponse is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/desiredProperties";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTIES, desiredProperties, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeatureDesiredPropertiesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeatureDesiredPropertiesResponse that = (RetrieveFeatureDesiredPropertiesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(desiredProperties, that.desiredProperties) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, desiredProperties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId + ", desiredProperties=" + desiredProperties + "]";
    }

}
