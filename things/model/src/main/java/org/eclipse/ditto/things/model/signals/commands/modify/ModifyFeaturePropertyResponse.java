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
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link ModifyFeatureProperty} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyFeaturePropertyResponse.TYPE)
public final class ModifyFeaturePropertyResponse extends AbstractCommandResponse<ModifyFeaturePropertyResponse>
        implements ThingModifyCommandResponse<ModifyFeaturePropertyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatureProperty.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_PROPERTY =
            JsonFieldDefinition.ofString("property", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_VALUE =
            JsonFieldDefinition.ofJsonValue("value", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyFeaturePropertyResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                JsonPointer.of(jsonObject.getValueOrThrow(JSON_PROPERTY)),
                                jsonObject.getValue(JSON_VALUE).orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    private final JsonPointer featurePropertyPointer;
    @Nullable private final JsonValue featurePropertyValue;

    private ModifyFeaturePropertyResponse(final ThingId thingId,
            final String featureId,
            final JsonPointer featurePropertyPointer,
            @Nullable final JsonValue featurePropertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = ConditionChecker.checkArgument(checkNotNull(featureId, "featureId"),
                fid -> !fid.trim().isEmpty(),
                () -> "The featureId must neither be empty nor blank.");
        this.featurePropertyPointer = checkPropertyPointer(featurePropertyPointer);
        this.featurePropertyValue = featurePropertyValue;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != featurePropertyValue) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Feature property value <{0}> is illegal in conjunction with <{1}>.",
                            featurePropertyValue,
                            httpStatus)
            );
        }
    }

    private static JsonPointer checkPropertyPointer(final JsonPointer propertyPointer) {
        checkNotNull(propertyPointer, "featurePropertyPointer");
        return ThingsModelFactory.validateFeaturePropertyPointer(propertyPointer);
    }

    /**
     * Returns a new {@code ModifyFeaturePropertyResponse} for a created FeatureProperty. This corresponds to the HTTP
     * status {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created feature property.
     * @param featureId the {@code Feature}'s ID whose Property was created.
     * @param featurePropertyPointer the pointer of the created FeatureProperty.
     * @param featureValue the created FeatureProperty value.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created FeatureProperty.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturePropertyResponse created(final ThingId thingId,
            final String featureId,
            final JsonPointer featurePropertyPointer,
            final JsonValue featureValue,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId,
                featureId,
                featurePropertyPointer,
                checkNotNull(featureValue, "featureValue"),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeaturePropertyResponse} for a modified FeatureProperty. This corresponds to the HTTP
     * status {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified feature property.
     * @param featureId the {@code Feature}'s ID whose Property was modified.
     * @param featurePropertyPointer the pointer of the modified FeatureProperty.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified FeatureProperty.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturePropertyResponse modified(final ThingId thingId,
            final String featureId,
            final JsonPointer featurePropertyPointer,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, featurePropertyPointer, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyFeaturePropertyResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature property was modified.
     * @param featureId ID of feature of which the property was modified.
     * @param featurePropertyPointer pointer to the modified feature property.
     * @param featurePropertyValue the modified feature property or {@code null} if an existing property was modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyFeaturePropertyResponse} instance.
     * @throws NullPointerException if any argument but {@code featurePropertyValue} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyFeaturePropertyResponse}
     * or if {@code httpStatus} contradicts {@code featurePropertyValue}.
     * @since 2.3.0
     */
    public static ModifyFeaturePropertyResponse newInstance(final ThingId thingId,
            final String featureId,
            final JsonPointer featurePropertyPointer,
            @Nullable final JsonValue featurePropertyValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeaturePropertyResponse(thingId,
                featureId,
                featurePropertyPointer,
                featurePropertyValue,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyFeaturePropertyResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureProperty} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyFeaturePropertyResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatureProperty} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyFeaturePropertyResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the pointer of the modified {@code FeatureProperty}.
     *
     * @return the pointer of the modified FeatureProperty.
     */
    public JsonPointer getFeaturePropertyPointer() {
        return featurePropertyPointer;
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
     * Returns the created {@code FeatureProperty}.
     *
     * @return the created FeatureProperty.
     */
    public Optional<JsonValue> getFeaturePropertyValue() {
        return Optional.ofNullable(featurePropertyValue);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(featurePropertyValue);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features/" + featureId + "/properties" + featurePropertyPointer);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_PROPERTY, featurePropertyPointer.toString(), predicate);
        if (null != featurePropertyValue) {
            jsonObjectBuilder.set(JSON_VALUE, featurePropertyValue, predicate);
        }
    }

    @Override
    public ModifyFeaturePropertyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId,
                featureId,
                featurePropertyPointer,
                featurePropertyValue,
                getHttpStatus(),
                dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyFeaturePropertyResponse;
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
        final ModifyFeaturePropertyResponse that = (ModifyFeaturePropertyResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(featurePropertyPointer, that.featurePropertyPointer) &&
                Objects.equals(featurePropertyValue, that.featurePropertyValue) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, featurePropertyPointer, featurePropertyValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId + ", featurePropertyPointer=" + featurePropertyPointer +
                ", featurePropertyValue=" + featurePropertyValue + "]";
    }

}
