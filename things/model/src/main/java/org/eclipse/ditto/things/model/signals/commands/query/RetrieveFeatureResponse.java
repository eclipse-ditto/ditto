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
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveFeature} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveFeatureResponse.TYPE)
public final class RetrieveFeatureResponse extends AbstractCommandResponse<RetrieveFeatureResponse>
        implements ThingQueryCommandResponse<RetrieveFeatureResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeature.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE =
            JsonFieldDefinition.ofJsonObject("feature", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveFeatureResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                ThingsModelFactory.newFeatureBuilder(jsonObject.getValueOrThrow(JSON_FEATURE))
                                        .useId(jsonObject.getValueOrThrow(JSON_FEATURE_ID))
                                        .build(),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final Feature feature;

    private RetrieveFeatureResponse(final ThingId thingId,
            final Feature feature,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.feature = checkNotNull(feature, "feature");
    }

    /**
     * Creates a response to a {@link RetrieveFeature} command.
     *
     * @param thingId the Thing ID of the retrieved feature.
     * @param featureId the identifier of the retrieved Feature.
     * @param jsonObject the retrieved Feature JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument but {@code jsonObject} is {@code null}.
     */
    public static RetrieveFeatureResponse of(final ThingId thingId,
            final String featureId,
            @Nullable final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        checkNotNull(featureId, "featureId");
        final Feature feature = null != jsonObject
                ? ThingsModelFactory.newFeatureBuilder(jsonObject).useId(featureId).build()
                : ThingsModelFactory.nullFeature(featureId);

        return of(thingId, feature, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeature} command.
     *
     * @param thingId the Thing ID of the retrieved feature.
     * @param feature the retrieved Feature.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeatureResponse of(final ThingId thingId,
            final Feature feature,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, feature, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveFeatureResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature belong to.
     * @param feature the retrieved Feature.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveFeatureResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code RetrieveFeatureResponse}.
     * @since 2.3.0
     */
    public static RetrieveFeatureResponse newInstance(final ThingId thingId,
            final Feature feature,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeatureResponse(thingId,
                feature,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveFeatureResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeature} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveFeatureResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeature} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveFeatureResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the identifier of the {@code Feature} whose properties were modified.
     *
     * @return the identifier.
     */
    public String getFeatureId() {
        return feature.getId();
    }

    /**
     * Returns the retrieved Feature.
     *
     * @return the retrieved Feature.
     */
    public Feature getFeature() {
        return feature;
    }

    @Override
    public JsonObject getEntity(final JsonSchemaVersion schemaVersion) {
        return feature.toJson(schemaVersion);
    }

    @Override
    public RetrieveFeatureResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        if (!entity.isObject()) {
            throw new IllegalArgumentException(MessageFormat.format("Entity is not a JSON object but <{0}>.", entity));
        }
        return of(thingId, getFeatureId(), entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveFeatureResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, feature, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features/" + getFeatureId());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, getFeatureId(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE, getEntity(schemaVersion), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeatureResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeatureResponse that = (RetrieveFeatureResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(feature, that.feature) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, feature);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", thingId=" + thingId
                + ", feature=" + feature
                + "]";
    }

}
