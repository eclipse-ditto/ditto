/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveFeatureDefinition} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveFeatureDefinitionResponse.TYPE)
public final class RetrieveFeatureDefinitionResponse extends AbstractCommandResponse<RetrieveFeatureDefinitionResponse>
        implements ThingQueryCommandResponse<RetrieveFeatureDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureDefinition.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_DEFINITION =
            JsonFieldDefinition.ofJsonArray("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveFeatureDefinitionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                jsonObject.getValueOrThrow(JSON_DEFINITION),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    private final JsonArray definition;

    private RetrieveFeatureDefinitionResponse(final ThingId thingId,
            final String featureId,
            final JsonArray definition,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = thingId;
        this.featureId = checkNotNull(featureId, "featureId");
        this.definition = checkNotNull(definition, "definition");
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDefinition} command.
     *
     * @param thingId the Thing ID of the retrieved Feature Definition.
     * @param featureId the identifier of the Feature whose Definition was retrieved.
     * @param definition the retrieved FeatureDefinition.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeatureDefinitionResponse of(final ThingId thingId,
            final String featureId,
            final FeatureDefinition definition,
            final DittoHeaders dittoHeaders) {

        checkNotNull(definition, "Definition");
        return newInstance(thingId, featureId, definition.toJson(), HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureDefinition} command.
     *
     * @param thingId the Thing ID of the retrieved Feature Definition.
     * @param featureId the identifier of the Feature whose Definition was retrieved.
     * @param definitionJsonArray the retrieved FeatureDefinition JSON array.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeatureDefinitionResponse of(final ThingId thingId,
            final String featureId,
            final JsonArray definitionJsonArray,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, definitionJsonArray, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveFeatureDefinitionResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature definition belong to.
     * @param featureId the identifier of the Feature whose Definition was retrieved.
     * @param definitionJsonArray the retrieved FeatureDefinition JSON array.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveFeatureDefinitionResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code RetrieveFeatureDefinitionResponse}.
     * @since 2.3.0
     */
    public static RetrieveFeatureDefinitionResponse newInstance(final ThingId thingId,
            final String featureId,
            final JsonArray definitionJsonArray,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeatureDefinitionResponse(thingId,
                featureId,
                definitionJsonArray,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveFeatureDefinitionResponse.class),
                dittoHeaders);
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
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static RetrieveFeatureDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
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
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static RetrieveFeatureDefinitionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
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
        if (!entity.isArray()) {
            throw new IllegalArgumentException(MessageFormat.format("Entity is not a JSON array but <{0}>.", entity));
        }
        return newInstance(thingId, featureId, entity.asArray(), getHttpStatus(), getDittoHeaders());
    }

    @Override
    public RetrieveFeatureDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, featureId, definition, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features/" + featureId + "/definition");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
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
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(definition, that.definition) &&
                super.equals(o);
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
