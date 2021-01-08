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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
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
 * Response to a {@link ModifyFeatureDesiredProperties} command.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyFeatureDesiredPropertiesResponse.TYPE)
public final class ModifyFeatureDesiredPropertiesResponse
        extends AbstractCommandResponse<ModifyFeatureDesiredPropertiesResponse>
        implements ThingModifyCommandResponse<ModifyFeatureDesiredPropertiesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatureDesiredProperties.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_DESIRED_PROPERTIES =
            JsonFactory.newJsonObjectFieldDefinition("desiredProperties", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final String featureId;
    @Nullable private final FeatureProperties desiredPropertiesCreated;

    private ModifyFeatureDesiredPropertiesResponse(final ThingId thingId,
            final CharSequence featureId,
            @Nullable final FeatureProperties desiredPropertiesCreated,
            final HttpStatus statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = argumentNotEmpty(featureId, "featureId").toString();
        this.desiredPropertiesCreated = desiredPropertiesCreated;
    }


    /**
     * Returns a new {@code ModifyFeatureDesiredPropertiesResponse} for created desired properties of a
     * {@link org.eclipse.ditto.model.things.Feature}. This corresponds to the HTTP status code
     * {@link HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created desired properties.
     * @param featureId the {@code Feature}'s ID whose desired properties were created.
     * @param desiredProperties the created desired properties.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for created desired properties.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureDesiredPropertiesResponse created(final ThingId thingId,
            final CharSequence featureId,
            final FeatureProperties desiredProperties,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDesiredPropertiesResponse(thingId, featureId, desiredProperties, HttpStatus.CREATED,
                dittoHeaders);
    }


    /**
     * Returns a new {@code ModifyFeatureDesiredPropertiesResponse} for modified desired properties of a
     * {@link org.eclipse.ditto.model.things.Feature}. This corresponds to the HTTP status code
     * {@link HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified desired properties.
     * @param featureId the {@code Feature}'s ID whose desired properties were modified.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for modified desired properties.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyFeatureDesiredPropertiesResponse modified(final ThingId thingId, final CharSequence featureId,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDesiredPropertiesResponse(thingId, featureId, null, HttpStatus.NO_CONTENT,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDesiredProperties} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeatureDesiredPropertiesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDesiredProperties} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeatureDesiredPropertiesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<ModifyFeatureDesiredPropertiesResponse>(TYPE, jsonObject)
                .deserialize(httpStatus -> {
                    final String extractedThingId =
                            jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);

                    final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                    final FeatureProperties extractedFeatureCreated = jsonObject.getValue(JSON_DESIRED_PROPERTIES)
                            .map(ThingsModelFactory::newFeatureProperties)
                            .orElse(null);

                    return new ModifyFeatureDesiredPropertiesResponse(thingId,
                            extractedFeatureId,
                            extractedFeatureCreated,
                            httpStatus,
                            dittoHeaders);
                });
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    /**
     * Returns the ID of the {@code Feature} whose desired properties were modified.
     *
     * @return the ID.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the created desired properties.
     *
     * @return the created desired properties.
     */
    public Optional<FeatureProperties> getDesiredPropertiesCreated() {
        return Optional.ofNullable(desiredPropertiesCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(desiredPropertiesCreated);
    }

    /**
     * ModifyFeatureDesiredPropertiesResponse is only available in JsonSchemaVersion V_2.
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
        if (null != desiredPropertiesCreated) {
            jsonObjectBuilder.set(JSON_DESIRED_PROPERTIES, desiredPropertiesCreated.toJson(schemaVersion, thePredicate),
                    predicate);
        }
    }

    @Override
    public ModifyFeatureDesiredPropertiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return desiredPropertiesCreated != null
                ? created(thingId, featureId, desiredPropertiesCreated, dittoHeaders)
                : modified(thingId, featureId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyFeatureDesiredPropertiesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeatureDesiredPropertiesResponse that = (ModifyFeatureDesiredPropertiesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(desiredPropertiesCreated, that.desiredPropertiesCreated) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, desiredPropertiesCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", featureId=" +
                featureId + ", desiredPropertiesCreated=" + desiredPropertiesCreated + "]";
    }

}
