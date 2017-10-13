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

import static java.util.Objects.requireNonNull;
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
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyFeatureProperties} command.
 */
@Immutable
public final class ModifyFeaturePropertiesResponse extends AbstractCommandResponse<ModifyFeaturePropertiesResponse>
        implements ThingModifyCommandResponse<ModifyFeaturePropertiesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatureProperties.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE_PROPERTIES =
            JsonFactory.newJsonObjectFieldDefinition("properties", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final String featureId;
    @Nullable private final FeatureProperties featurePropertiesCreated;

    private ModifyFeaturePropertiesResponse(final String thingId, final String featureId,
            @Nullable final FeatureProperties featurePropertiesCreated,
            final HttpStatusCode statusCode, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.featureId = requireNonNull(featureId, "The Feature ID must not be null!");
        this.featurePropertiesCreated = featurePropertiesCreated;
    }

    /**
     * Returns a new {@code ModifyFeaturePropertiesResponse} for a created FeatureProperties. This corresponds to the
     * HTTP status code {@link HttpStatusCode#CREATED}.
     *
     * @param thingId the Thing ID of the created feature properties.
     * @param featureId the {@code Feature}'s ID whose Properties were created.
     * @param featureProperties the created FeatureProperties.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created FeatureProperties.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturePropertiesResponse created(final String thingId, final String featureId,
            final FeatureProperties featureProperties, final DittoHeaders dittoHeaders) {
        return new ModifyFeaturePropertiesResponse(thingId, featureId, featureProperties, HttpStatusCode.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeaturePropertiesResponse} for a modified FeatureProperties. This corresponds to the
     * HTTP status code {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified feature properties.
     * @param featureId the {@code Feature}'s ID whose Properties were modified.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified FeatureProperties.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyFeaturePropertiesResponse modified(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        return new ModifyFeaturePropertiesResponse(thingId, featureId, null, HttpStatusCode.NO_CONTENT, dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureProperties} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeaturePropertiesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureProperties} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeaturePropertiesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyFeaturePropertiesResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);

                    final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                    final FeatureProperties extractedFeatureCreated = jsonObject.getValue(JSON_FEATURE_PROPERTIES)
                            .map(ThingsModelFactory::newFeatureProperties)
                            .orElse(null);

                    return new ModifyFeaturePropertiesResponse(thingId, extractedFeatureId, extractedFeatureCreated,
                            statusCode, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the ID of the {@code Feature} whose properties were modified.
     *
     * @return the ID.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the created {@code FeatureProperties}.
     *
     * @return the created FeatureProperties.
     */
    public Optional<FeatureProperties> getFeaturePropertiesCreated() {
        return Optional.ofNullable(featurePropertiesCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(featurePropertiesCreated);
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
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        if (null != featurePropertiesCreated) {
            jsonObjectBuilder.set(JSON_FEATURE_PROPERTIES, featurePropertiesCreated.toJson(schemaVersion, thePredicate),
                    predicate);
        }
    }

    @Override
    public ModifyFeaturePropertiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return featurePropertiesCreated != null ?
                created(thingId, featureId, featurePropertiesCreated, dittoHeaders) :
                modified(thingId, featureId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyFeaturePropertiesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeaturePropertiesResponse that = (ModifyFeaturePropertiesResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(featureId, that.featureId)
                && Objects.equals(featurePropertiesCreated, that.featurePropertiesCreated) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, featurePropertiesCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", featureId=" +
                featureId + ", featurePropertiesCreated=" + featurePropertiesCreated + "]";
    }

}
