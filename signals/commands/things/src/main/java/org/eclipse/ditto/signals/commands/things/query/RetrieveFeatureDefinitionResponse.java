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
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveFeatureDefinition} command.
 */
@Immutable
public final class RetrieveFeatureDefinitionResponse extends AbstractCommandResponse<RetrieveFeatureDefinitionResponse>
        implements ThingQueryCommandResponse<RetrieveFeatureDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureDefinition.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_DEFINITION =
            JsonFactory.newJsonArrayFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final String featureId;
    private final JsonArray definition;

    private RetrieveFeatureDefinitionResponse(final String thingId,
            final String featureId,
            final JsonArray definition,
            final DittoHeaders dittoHeaders) {

        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.featureId = checkNotNull(featureId, "Feature ID");
        this.definition = checkNotNull(definition, "Feature Definition");
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDefinition} command.
     *
     * @param thingId the Thing ID of the retrieved Feature Definition.
     * @param featureId the identifier of the Feature whose Definition was retrieved.
     * @param definition the retrieved FeatureDefinition.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeatureDefinitionResponse of(final String thingId,
            final String featureId,
            final FeatureDefinition definition,
            final DittoHeaders dittoHeaders) {

        checkNotNull(definition, "Definition");
        return new RetrieveFeatureDefinitionResponse(thingId, featureId, definition.toJson(), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDefinition} command.
     *
     * @param thingId the Thing ID of the retrieved Feature Definition.
     * @param featureId the identifier of the Feature whose Definition was retrieved.
     * @param definitionJsonArray the retrieved FeatureDefinition JSON array.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeatureDefinitionResponse of(final String thingId,
            final String featureId,
            final JsonArray definitionJsonArray,
            final DittoHeaders dittoHeaders) {

        return of(thingId, featureId, FeatureDefinition.fromJson(definitionJsonArray), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDefinition} command from a JSON string.
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
     *     <li>{@link ThingQueryCommandResponse.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID} or</li>
     *     <li>{@link #JSON_DEFINITION}.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveFeatureDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDefinition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain any of
     * the required fields
     * <ul>
     *     <li>{@link ThingQueryCommandResponse.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID} or</li>
     *     <li>{@link #JSON_DEFINITION}.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveFeatureDefinitionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<RetrieveFeatureDefinitionResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final String extractedThingId =
                            jsonObject.getValueOrThrow(ThingQueryCommandResponse.JsonFields.JSON_THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                    final JsonArray extractedFeatureDefinition = jsonObject.getValueOrThrow(JSON_DEFINITION);

                    return of(extractedThingId, extractedFeatureId, extractedFeatureDefinition, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the identifier of the {@code Feature} for which to retrieve the Definition.
     *
     * @return the identifier.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the retrieved FeatureDefinition.
     *
     * @return the retrieved FeatureDefinition.
     */
    public FeatureDefinition getDefinition() {
        return ThingsModelFactory.newFeatureDefinition(definition);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return definition;
    }

    @Override
    public RetrieveFeatureDefinitionResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(thingId, featureId, entity.asArray(), getDittoHeaders());
    }

    @Override
    public RetrieveFeatureDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, definition, dittoHeaders);
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
        jsonObjectBuilder.set(ThingQueryCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DEFINITION, definition, predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeatureDefinitionResponse that = (RetrieveFeatureDefinitionResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(featureId, that.featureId)
                && Objects.equals(definition, that.definition) && super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeatureDefinitionResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, definition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId + ", definition=" + definition + "]";
    }

}
