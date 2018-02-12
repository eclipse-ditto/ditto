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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
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
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyFeatureDefinition} command.
 */
@Immutable
public final class ModifyFeatureDefinitionResponse extends AbstractCommandResponse<ModifyFeatureDefinitionResponse>
        implements ThingModifyCommandResponse<ModifyFeatureDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatureDefinition.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_DEFINITION =
            JsonFactory.newJsonArrayFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final String featureId;
    @Nullable private final FeatureDefinition definitionCreated;

    private ModifyFeatureDefinitionResponse(final String thingId,
            final String featureId,
            @Nullable final FeatureDefinition definitionCreated,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.featureId = requireNonNull(featureId, "The Feature ID must not be null!");
        this.definitionCreated = definitionCreated;
    }

    /**
     * Returns a new {@code ModifyFeatureDefinitionResponse} for a created FeatureDefinition. This corresponds to the
     * HTTP status code {@link HttpStatusCode#CREATED}.
     *
     * @param thingId the Thing ID of the created Feature Definition.
     * @param featureId the {@code Feature}'s ID whose Definition were created.
     * @param definitionCreated the created FeatureDefinition.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created FeatureDefinition.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureDefinitionResponse created(final String thingId,
            final String featureId,
            final FeatureDefinition definitionCreated,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDefinitionResponse(thingId, featureId, definitionCreated, HttpStatusCode.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeatureDefinitionResponse} for a modified FeatureDefinition. This corresponds to the
     * HTTP status code {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified Feature Definition.
     * @param featureId the {@code Feature}'s ID whose Definition were modified.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified FeatureDefinition.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyFeatureDefinitionResponse modified(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDefinitionResponse(thingId, featureId, null, HttpStatusCode.NO_CONTENT, dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDefinition} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the parsed {@code jsonString} did not contain any of
     * the required fields
     * <ul>
     *     <li>{@link ThingModifyCommandResponse.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID} or</li>
     *     <li>{@link #JSON_DEFINITION}.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyFeatureDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDefinition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain any of the
     * required fields
     * <ul>
     *     <li>{@link ThingModifyCommandResponse.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID} or</li>
     *     <li>{@link #JSON_DEFINITION}.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyFeatureDefinitionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<ModifyFeatureDefinitionResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final String extractedThingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                    final FeatureDefinition extractedFeatureDefinition = jsonObject.getValue(JSON_DEFINITION)
                            .map(ThingsModelFactory::newFeatureDefinition)
                            .orElse(null);

                    return new ModifyFeatureDefinitionResponse(extractedThingId, extractedFeatureId,
                            extractedFeatureDefinition, statusCode, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the ID of the {@code Feature} whose Definition was modified.
     *
     * @return the ID.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the created {@code FeatureDefinition}.
     *
     * @return the created FeatureDefinition.
     */
    public Optional<FeatureDefinition> getFeatureDefinitionCreated() {
        return Optional.ofNullable(definitionCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(definitionCreated).map(Jsonifiable::toJson);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/definition";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        if (null != definitionCreated) {
            jsonObjectBuilder.set(JSON_DEFINITION, definitionCreated.toJson(), predicate);
        }
    }

    @Override
    public ModifyFeatureDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return definitionCreated != null ?
                created(thingId, featureId, definitionCreated, dittoHeaders) :
                modified(thingId, featureId, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeatureDefinitionResponse that = (ModifyFeatureDefinitionResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(featureId, that.featureId)
                && Objects.equals(definitionCreated, that.definitionCreated) && super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyFeatureDefinitionResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, definitionCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", featureId=" +
                featureId + ", definitionCreated=" + definitionCreated + "]";
    }

}
