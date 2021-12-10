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
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link ModifyFeatureDesiredProperty} command.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyFeatureDesiredPropertyResponse.TYPE)
public final class ModifyFeatureDesiredPropertyResponse
        extends AbstractCommandResponse<ModifyFeatureDesiredPropertyResponse>
        implements ThingModifyCommandResponse<ModifyFeatureDesiredPropertyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatureDesiredProperty.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_DESIRED_PROPERTY =
            JsonFieldDefinition.ofString("desiredProperty", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_DESIRED_VALUE =
            JsonFieldDefinition.ofJsonValue("desiredValue", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyFeatureDesiredPropertyResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                JsonPointer.of(jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY)),
                                jsonObject.getValue(JSON_DESIRED_VALUE).orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    private final JsonPointer desiredPropertyPointer;
    @Nullable private final JsonValue desiredPropertyValue;

    private ModifyFeatureDesiredPropertyResponse(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            @Nullable final JsonValue desiredPropertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = ConditionChecker.checkArgument(checkNotNull(featureId, "featureId").toString(),
                featureIdArgument -> !featureIdArgument.trim().isEmpty(),
                () -> "The featureId must neither be empty nor blank.");
        this.desiredPropertyPointer = checkDesiredPropertyPointer(desiredPropertyPointer);
        this.desiredPropertyValue = desiredPropertyValue;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != desiredPropertyValue) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Desired property value <{0}> is illegal in conjunction with <{1}>.",
                            desiredPropertyValue,
                            httpStatus)
            );
        }
    }

    private static JsonPointer checkDesiredPropertyPointer(final JsonPointer desiredPropertyPointer) {
        checkNotNull(desiredPropertyPointer, "desiredPropertyPointer");
        return ThingsModelFactory.validateFeaturePropertyPointer(desiredPropertyPointer);
    }

    /**
     * Returns a new {@code ModifyFeatureDesiredPropertyResponse} for a created desired property. This corresponds to the HTTP
     * status {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created desired property.
     * @param featureId the {@code Feature}'s ID whose desired property was created.
     * @param desiredPropertyPointer the pointer of the created desired property.
     * @param desiredValue the created desired property's value.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created desired property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureDesiredPropertyResponse created(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final JsonValue desiredValue,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId,
                featureId,
                desiredPropertyPointer,
                checkNotNull(desiredValue, "desiredValue"),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeatureDesiredPropertyResponse} for a modified desired property. This corresponds to the HTTP
     * status {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified desired property.
     * @param featureId the {@code Feature}'s ID whose desired property was modified.
     * @param desiredPropertyPointer the pointer of the modified desired property.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified desired property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureDesiredPropertyResponse modified(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, desiredPropertyPointer, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyFeatureDesiredPropertyResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature belongs to.
     * @param featureId ID of the feature of which the desired properties were modified.
     * @param desiredPropertyPointer the pointer of the created or modified desired property.
     * @param desiredPropertyValue the created desired property value or {@code null} if an existing one was modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyFeatureDesiredPropertyResponse} instance.
     * @throws NullPointerException if any argument but {@code desiredPropertyValue} is {@code null}.
     * @throws IllegalArgumentException if {@code featureId} is empty or blank or if {@code httpStatus} is not allowed
     * for a {@code ModifyFeatureDesiredPropertyResponse} or if {@code httpStatus} contradicts
     * {@code desiredPropertyValue}.
     * @since 2.3.0
     */
    public static ModifyFeatureDesiredPropertyResponse newInstance(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            @Nullable final JsonValue desiredPropertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDesiredPropertyResponse(thingId,
                featureId,
                desiredPropertyPointer,
                desiredPropertyValue,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyFeatureDesiredPropertyResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDesiredProperty} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of the desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyFeatureDesiredPropertyResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureDesiredProperty} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of the desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyFeatureDesiredPropertyResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the pointer of the modified desired property.
     *
     * @return the pointer of the modified desired property.
     */
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
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
     * Returns the created desired property.
     *
     * @return the created desired property.
     */
    public Optional<JsonValue> getDesiredPropertyValue() {
        return Optional.ofNullable(desiredPropertyValue);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(desiredPropertyValue);
    }

    /**
     * ModifyFeatureDesiredPropertyResponse is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features/" + featureId + "/desiredProperties" + desiredPropertyPointer);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTY, desiredPropertyPointer.toString(), predicate);
        if (null != desiredPropertyValue) {
            jsonObjectBuilder.set(JSON_DESIRED_VALUE, desiredPropertyValue, predicate);
        }
    }

    @Override
    public ModifyFeatureDesiredPropertyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId,
                featureId,
                desiredPropertyPointer,
                desiredPropertyValue,
                getHttpStatus(),
                dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyFeatureDesiredPropertyResponse;
    }

    @SuppressWarnings("squid:S1067")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeatureDesiredPropertyResponse that = (ModifyFeatureDesiredPropertyResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(desiredPropertyPointer, that.desiredPropertyPointer) &&
                Objects.equals(desiredPropertyValue, that.desiredPropertyValue) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, desiredPropertyPointer, desiredPropertyValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId
                + ", desiredPropertyPointer=" + desiredPropertyPointer
                + ", desiredPropertyValue=" + desiredPropertyValue + "]";
    }

}
