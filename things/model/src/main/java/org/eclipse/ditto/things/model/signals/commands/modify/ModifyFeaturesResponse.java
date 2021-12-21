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
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link ModifyFeatures} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyFeaturesResponse.TYPE)
public final class ModifyFeaturesResponse extends AbstractCommandResponse<ModifyFeaturesResponse>
        implements ThingModifyCommandResponse<ModifyFeaturesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyFeatures.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURES =
            JsonFieldDefinition.ofJsonObject("features", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyFeaturesResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                ThingsModelFactory.newFeatures(jsonObject.getValueOrThrow(JSON_FEATURES)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final Features features;

    private ModifyFeaturesResponse(final ThingId thingId,
            final Features features,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.features = checkNotNull(features, "features");
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && !features.isNull()) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Features <{0}> are illegal in conjunction with <{1}>.",
                            features,
                            httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyFeaturesResponse} for a created Feature. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created features.
     * @param features the created Features.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Feature.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturesResponse created(final ThingId thingId,
            final Features features,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, features, HttpStatus.CREATED, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeaturesResponse} for a modified Feature. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified features.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Feature.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeaturesResponse modified(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return newInstance(thingId, ThingsModelFactory.nullFeatures(), HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyFeaturesResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the features belong to.
     * @param httpStatus the status of the response.
     * @param features the {@code Features} that were created or the result of {@link ThingsModelFactory#nullFeatures()}
     * if existing attributes were modified.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyFeaturesResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyFeaturesResponse}
     * or if {@code httpStatus} contradicts {@code features}.
     * @since 2.3.0
     */
    public static ModifyFeaturesResponse newInstance(final ThingId thingId,
            final Features features,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeaturesResponse(thingId,
                features,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyFeaturesResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeatures} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeaturesResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyFeatures} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeaturesResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the created {@code Features}.
     *
     * @return the created Features.
     */
    public Optional<Features> getFeaturesCreated() {
        return Optional.of(features);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(features.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURES, features.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public ModifyFeaturesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, features, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyFeaturesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeaturesResponse that = (ModifyFeaturesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(features, that.features) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, features);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", features=" +
                features + "]";
    }

}
