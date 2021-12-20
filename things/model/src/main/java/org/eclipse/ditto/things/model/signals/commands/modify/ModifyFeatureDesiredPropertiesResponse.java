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
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

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
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_DESIRED_PROPERTIES =
            JsonFieldDefinition.ofJsonObject("desiredProperties", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyFeatureDesiredPropertiesResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                jsonObject.getValue(JSON_DESIRED_PROPERTIES)
                                        .map(ThingsModelFactory::newFeatureProperties)
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    @Nullable private final FeatureProperties desiredPropertiesCreated;

    private ModifyFeatureDesiredPropertiesResponse(final ThingId thingId,
            final CharSequence featureId,
            @Nullable final FeatureProperties desiredPropertiesCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = ConditionChecker.checkArgument(checkNotNull(featureId, "featureId").toString(),
                featureIdString -> !featureIdString.trim().isEmpty(),
                () -> "The featureId must neither be empty nor blank.");
        this.desiredPropertiesCreated = desiredPropertiesCreated;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != desiredPropertiesCreated) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Desired FeatureProperties <{0}> is illegal in conjunction with <{1}>.",
                            desiredPropertiesCreated,
                            httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyFeatureDesiredPropertiesResponse} for created desired properties of a
     * {@link org.eclipse.ditto.things.model.Feature}. This corresponds to the HTTP status {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
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

        return newInstance(thingId,
                featureId,
                checkNotNull(desiredProperties, "desiredProperties"),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeatureDesiredPropertiesResponse} for modified desired properties of a
     * {@link org.eclipse.ditto.things.model.Feature}. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified desired properties.
     * @param featureId the {@code Feature}'s ID whose desired properties were modified.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for modified desired properties.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureDesiredPropertiesResponse modified(final ThingId thingId,
            final CharSequence featureId,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyFeatureDesiredPropertiesResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature desired properties were modified.
     * @param featureId ID of the feature of which the desired properties were modified.
     * @param desiredPropertiesCreated the created desired properties or {@code null} if existing desired properties
     * were modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyFeatureDesiredPropertiesResponse} instance.
     * @throws NullPointerException if any argument but {@code desiredPropertiesCreated} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code ModifyFeatureDesiredPropertiesResponse} or if {@code httpStatus} contradicts
     * {@code desiredPropertiesCreated}.
     * @since 2.3.0
     */
    public static ModifyFeatureDesiredPropertiesResponse newInstance(final ThingId thingId,
            final CharSequence featureId,
            @Nullable final FeatureProperties desiredPropertiesCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDesiredPropertiesResponse(thingId,
                featureId,
                desiredPropertiesCreated,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyFeatureDesiredPropertiesResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDesiredProperties} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeatureDesiredPropertiesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDesiredProperties} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeatureDesiredPropertiesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
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
        return JsonPointer.of("/features/" + featureId + "/desiredProperties");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
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
        return newInstance(thingId, featureId, desiredPropertiesCreated, getHttpStatus(), dittoHeaders);
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
