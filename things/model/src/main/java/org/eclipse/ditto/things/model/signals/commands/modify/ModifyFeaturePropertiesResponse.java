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
 * Response to a {@link ModifyFeatureProperties} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyFeaturePropertiesResponse.TYPE)
public final class ModifyFeaturePropertiesResponse extends AbstractCommandResponse<ModifyFeaturePropertiesResponse>
        implements ThingModifyCommandResponse<ModifyFeaturePropertiesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatureProperties.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE_PROPERTIES =
            JsonFieldDefinition.ofJsonObject("properties", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyFeaturePropertiesResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                jsonObject.getValue(JSON_FEATURE_PROPERTIES)
                                        .map(ThingsModelFactory::newFeatureProperties)
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    @Nullable private final FeatureProperties featurePropertiesCreated;

    private ModifyFeaturePropertiesResponse(final ThingId thingId,
            final String featureId,
            @Nullable final FeatureProperties featurePropertiesCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = ConditionChecker.checkArgument(checkNotNull(featureId, "featureId"),
                fid -> !fid.trim().isEmpty(),
                () -> "The featureId must neither be empty nor blank.");
        this.featurePropertiesCreated = featurePropertiesCreated;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != featurePropertiesCreated) {
            throw new IllegalArgumentException(
                    MessageFormat.format("FeatureProperties <{0}> is illegal in conjunction with <{1}>.",
                            featurePropertiesCreated,
                            httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyFeaturePropertiesResponse} for a created FeatureProperties. This corresponds to the
     * HTTP status {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created feature properties.
     * @param featureId the {@code Feature}'s ID whose Properties were created.
     * @param featureProperties the created FeatureProperties.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created FeatureProperties.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturePropertiesResponse created(final ThingId thingId,
            final String featureId,
            final FeatureProperties featureProperties,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId,
                featureId,
                checkNotNull(featureProperties, "featureProperties"),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeaturePropertiesResponse} for a modified FeatureProperties. This corresponds to the
     * HTTP status {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified feature properties.
     * @param featureId the {@code Feature}'s ID whose Properties were modified.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified FeatureProperties.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturePropertiesResponse modified(final ThingId thingId,
            final String featureId,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyFeaturePropertiesResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature belongs to.
     * @param featureId ID of feature the properties were modified feature or created.
     * @param featurePropertiesCreated the create feature properties or {@code null} if existing properties were
     * modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyFeaturePropertiesResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code ModifyFeaturePropertiesResponse} or if {@code httpStatus} contradicts {@code featurePropertiesCreated}.
     * @since 2.3.0
     */
    public static ModifyFeaturePropertiesResponse newInstance(final ThingId thingId,
            final String featureId,
            @Nullable final FeatureProperties featurePropertiesCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeaturePropertiesResponse(thingId,
                featureId,
                featurePropertiesCreated,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyFeaturePropertiesResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureProperties} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeaturePropertiesResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureProperties} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeaturePropertiesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
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
        return JsonPointer.of("/features/" + featureId + "/properties");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        if (null != featurePropertiesCreated) {
            jsonObjectBuilder.set(JSON_FEATURE_PROPERTIES, featurePropertiesCreated.toJson(schemaVersion, thePredicate),
                    predicate);
        }
    }

    @Override
    public ModifyFeaturePropertiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, featureId, featurePropertiesCreated, getHttpStatus(), dittoHeaders);
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
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(featurePropertiesCreated, that.featurePropertiesCreated) &&
                super.equals(o);
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
