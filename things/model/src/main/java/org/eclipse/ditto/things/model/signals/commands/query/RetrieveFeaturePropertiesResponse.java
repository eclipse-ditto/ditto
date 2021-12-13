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
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveFeatureProperties} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveFeaturePropertiesResponse.TYPE)
public final class RetrieveFeaturePropertiesResponse extends AbstractCommandResponse<RetrieveFeaturePropertiesResponse>
        implements ThingQueryCommandResponse<RetrieveFeaturePropertiesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatureProperties.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_PROPERTIES =
            JsonFieldDefinition.ofJsonObject("properties", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveFeaturePropertiesResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JSON_FEATURE_ID),
                                ThingsModelFactory.newFeatureProperties(jsonObject.getValueOrThrow(JSON_PROPERTIES)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String featureId;
    private final FeatureProperties featureProperties;

    private RetrieveFeaturePropertiesResponse(final ThingId thingId,
            final String featureId,
            final FeatureProperties featureProperties,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = checkNotNull(featureId, "featureId");
        this.featureProperties = checkNotNull(featureProperties, "featureProperties");
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command.
     *
     * @param thingId the Thing ID of the retrieved feature properties.
     * @param featureId the identifier of the Feature whose Properties were retrieved.
     * @param featureProperties the retrieved FeatureProperties.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeaturePropertiesResponse of(final ThingId thingId,
            final String featureId,
            final FeatureProperties featureProperties,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, featureId, featureProperties, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command.
     *
     * @param thingId the Thing ID of the retrieved feature properties.
     * @param featureId the identifier of the Feature whose Properties were retrieved.
     * @param jsonObject the retrieved FeatureProperties JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeaturePropertiesResponse of(final ThingId thingId,
            final String featureId,
            @Nullable final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final FeatureProperties featureProperties;
        if (jsonObject == null || jsonObject.isNull()) {
            featureProperties = ThingsModelFactory.nullFeatureProperties();
        } else {
            featureProperties = ThingsModelFactory.newFeatureProperties(jsonObject);
        }

        return of(thingId, featureId, featureProperties, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveFeaturePropertiesResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature properties belong to.
     * @param featureId the identifier of the feature whose properties were retrieved.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveFeaturePropertiesResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code RetrieveFeaturePropertiesResponse}.
     * @since 2.3.0
     */
    public static RetrieveFeaturePropertiesResponse newInstance(final ThingId thingId,
            final String featureId,
            final FeatureProperties featureProperties,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeaturePropertiesResponse(thingId,
                featureId,
                featureProperties,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveFeaturePropertiesResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveFeaturePropertiesResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatureProperties} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveFeaturePropertiesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

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
        return featureId;
    }

    /**
     * Returns the retrieved FeatureProperties.
     *
     * @return the retrieved FeatureProperties.
     */
    public FeatureProperties getFeatureProperties() {
        return featureProperties;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return featureProperties.toJson(schemaVersion);
    }

    @Override
    public RetrieveFeaturePropertiesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        if (!entity.isObject()) {
            throw new IllegalArgumentException(MessageFormat.format("Entity is not a JSON object but <{0}>.", entity));
        }
        return newInstance(thingId,
                featureId,
                ThingsModelFactory.newFeatureProperties(entity.asObject()),
                getHttpStatus(),
                getDittoHeaders());
    }

    @Override
    public RetrieveFeaturePropertiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, featureId, featureProperties, getHttpStatus(), dittoHeaders);
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
        jsonObjectBuilder.set(JSON_PROPERTIES, featureProperties, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeaturePropertiesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeaturePropertiesResponse that = (RetrieveFeaturePropertiesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(featureProperties, that.featureProperties) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, featureProperties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId + ", featureProperties=" + featureProperties + "]";
    }

}
