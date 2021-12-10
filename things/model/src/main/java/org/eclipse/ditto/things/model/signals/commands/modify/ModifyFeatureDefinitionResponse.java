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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
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
 * Response to a {@link ModifyFeatureDefinition} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyFeatureDefinitionResponse.TYPE)
public final class ModifyFeatureDefinitionResponse extends AbstractCommandResponse<ModifyFeatureDefinitionResponse>
        implements ThingModifyCommandResponse<ModifyFeatureDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatureDefinition.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_DEFINITION =
            JsonFieldDefinition.ofJsonArray("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyFeatureDefinitionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                jsonObject.getValue(JSON_DEFINITION)
                                        .map(ThingsModelFactory::newFeatureDefinition)
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    @Nullable private final FeatureDefinition definitionCreated;

    private ModifyFeatureDefinitionResponse(final ThingId thingId,
            final String featureId,
            @Nullable final FeatureDefinition definitionCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = ConditionChecker.checkArgument(checkNotNull(featureId, "featureId"),
                fid -> !fid.trim().isEmpty(),
                () -> "The featureId must neither be empty nor blank.");
        this.definitionCreated = definitionCreated;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != definitionCreated) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Feature definition <{0}> is illegal in conjunction with <{1}>.",
                    definitionCreated,
                    httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyFeatureDefinitionResponse} for a created FeatureDefinition. This corresponds to the
     * HTTP status {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created Feature Definition.
     * @param featureId the {@code Feature}'s ID whose Definition were created.
     * @param definitionCreated the created FeatureDefinition.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created FeatureDefinition.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureDefinitionResponse created(final ThingId thingId,
            final String featureId,
            final FeatureDefinition definitionCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId,
                featureId,
                checkNotNull(definitionCreated, "definitionCreated"),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeatureDefinitionResponse} for a modified FeatureDefinition. This corresponds to the
     * HTTP status {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified Feature Definition.
     * @param featureId the {@code Feature}'s ID whose Definition were modified.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified FeatureDefinition.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureDefinitionResponse modified(final ThingId thingId,
            final String featureId,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyFeatureDefinitionResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the attribute belongs to.
     * @param featureId ID of feature of which the definition was modified.
     * @param definitionCreated the created feature definition or {@code null} if an existing was modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyFeatureDefinitionResponse} instance.
     * @throws NullPointerException if any argument but {@code definitionCreated} is {@code null}.
     * @throws IllegalArgumentException if {@code featureId} is empty or blank or if {@code httpStatus} is not allowed
     * for a {@code ModifyFeatureDefinitionResponse} or if {@code httpStatus} contradicts {@code definitionCreated}.
     * @since 2.3.0
     */
    public static ModifyFeatureDefinitionResponse newInstance(final ThingId thingId,
            final String featureId,
            @Nullable final FeatureDefinition definitionCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDefinitionResponse(thingId,
                featureId,
                definitionCreated,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyFeatureDefinitionResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDefinition} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the parsed {@code jsonString} did not contain any of
     * the required fields
     * <ul>
     *     <li>{@link org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID} or</li>
     *     <li>{@link #JSON_DEFINITION}.</li>
     * </ul>
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static ModifyFeatureDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDefinition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain any of the
     * required fields
     * <ul>
     *     <li>{@link org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse.JsonFields#JSON_THING_ID},</li>
     *     <li>{@link #JSON_FEATURE_ID} or</li>
     *     <li>{@link #JSON_DEFINITION}.</li>
     * </ul>
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static ModifyFeatureDefinitionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
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
        return JsonPointer.of("/features/" + featureId + "/definition");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        if (null != definitionCreated) {
            jsonObjectBuilder.set(JSON_DEFINITION, definitionCreated.toJson(), predicate);
        }
    }

    @Override
    public ModifyFeatureDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, featureId, definitionCreated, getHttpStatus(), dittoHeaders);
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
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(definitionCreated, that.definitionCreated) &&
                super.equals(o);
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
