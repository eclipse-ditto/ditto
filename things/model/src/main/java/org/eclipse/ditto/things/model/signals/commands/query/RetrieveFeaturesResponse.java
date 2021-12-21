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
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveFeatures} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveFeaturesResponse.TYPE)
public final class RetrieveFeaturesResponse extends AbstractCommandResponse<RetrieveFeaturesResponse>
        implements ThingQueryCommandResponse<RetrieveFeaturesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveFeatures.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURES =
            JsonFieldDefinition.ofJsonObject("features", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveFeaturesResponse> JSON_DESERIALIZER =
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

    private RetrieveFeaturesResponse(final ThingId thingId,
            final Features features,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.features = checkNotNull(features, "features");
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command.
     *
     * @param thingId the Thing ID of the retrieved features.
     * @param features the retrieved Features.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveFeaturesResponse of(final ThingId thingId,
            final Features features,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, features, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command.
     *
     * @param thingId the Thing ID of the retrieved features.
     * @param jsonObject the retrieved Features.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static RetrieveFeaturesResponse of(final ThingId thingId,
            @Nullable final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final Features features = null != jsonObject
                ? ThingsModelFactory.newFeatures(jsonObject)
                : ThingsModelFactory.nullFeatures();

        return of(thingId, features, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveFeaturesResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the features belong to.
     * @param features the retrieved Features.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveFeaturesResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code RetrieveFeaturesResponse}.
     * @since 2.3.0
     */
    public static RetrieveFeaturesResponse newInstance(final ThingId thingId,
            final Features features,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeaturesResponse(thingId,
                features,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveFeaturesResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveFeaturesResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveFeatures} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveFeaturesResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the retrieved Features.
     *
     * @return the retrieved Features.
     */
    public Features getFeatures() {
        return features;
    }

    @Override
    public JsonObject getEntity(final JsonSchemaVersion schemaVersion) {
        return features.toJson(schemaVersion);
    }

    @Override
    public RetrieveFeaturesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        if (!entity.isObject()) {
            throw new IllegalArgumentException(MessageFormat.format("Entity is not a JSON object but <{0}>.", entity));
        }
        return newInstance(thingId,
                ThingsModelFactory.newFeatures(entity.asObject()),
                getHttpStatus(),
                getDittoHeaders());
    }

    @Override
    public RetrieveFeaturesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, features, getHttpStatus(), dittoHeaders);
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
        jsonObjectBuilder.set(JSON_FEATURES, getEntity(schemaVersion), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeaturesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveFeaturesResponse that = (RetrieveFeaturesResponse) o;
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
