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

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
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
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveThing} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveThingResponse.TYPE)
public final class RetrieveThingResponse extends AbstractCommandResponse<RetrieveThingResponse>
        implements ThingQueryCommandResponse<RetrieveThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + RetrieveThing.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFieldDefinition.ofJsonObject("thing", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_THING_PLAIN_JSON =
            JsonFieldDefinition.ofString("thingPlainJson", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveThingResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final JsonObject thingJsonObject = jsonObject.getValue(JSON_THING).orElse(null);
                        final String thingPlainJsonString = jsonObject.getValue(JSON_THING_PLAIN_JSON)
                                .orElseGet(() -> {
                                    if (null == thingJsonObject) {
                                        throw JsonMissingFieldException.newBuilder()
                                                .fieldName(JSON_THING.getPointer())
                                                .build();
                                    }
                                    return thingJsonObject.toString();
                                });

                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                thingJsonObject,
                                thingPlainJsonString,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String thingPlainJson;

    @Nullable private JsonObject thing;

    private RetrieveThingResponse(final ThingId thingId,
            @Nullable final JsonObject thing,
            final String thingPlainJson,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.thingPlainJson = checkNotNull(thingPlainJson, "thingPlainJson");
        this.thing = thing; // lazy init - might be null
    }

    /**
     * Creates a response to a {@link RetrieveThing} command.
     *
     * @param thingId the Thing ID of the retrieved Thing.
     * @param thing the retrieved Thing.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingResponse of(final ThingId thingId,
            final JsonObject thing,
            final DittoHeaders dittoHeaders) {

        checkNotNull(thingId, "thingId");
        return newInstance(thingId, thing, thing.toString(), HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command.
     *
     * @param thingId the Thing ID of the retrieved Thing.
     * @param thingPlainJson the retrieved Thing as plain JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingResponse of(final ThingId thingId, final String thingPlainJson,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, null, thingPlainJson, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command.
     *
     * @param thingId the Thing ID of the retrieved Thing.
     * @param thing the retrieved Thing.
     * @param fieldSelector the JsonFieldSelector to apply to the passed thing when transforming to JSON.
     * @param predicate the predicate to apply to the things when transforming to JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingResponse of(final ThingId thingId,
            final Thing thing,
            @Nullable final JsonFieldSelector fieldSelector,
            @Nullable final Predicate<JsonField> predicate,
            final DittoHeaders dittoHeaders) {

        final JsonObject thingJson = toThingJson(checkNotNull(thing, "thing"), fieldSelector, predicate, dittoHeaders);
        return newInstance(thingId, thingJson, thingJson.toString(), HTTP_STATUS, dittoHeaders);
    }

    private static JsonObject toThingJson(final Thing thing,
            @Nullable final JsonFieldSelector fieldSelector,
            @Nullable final Predicate<JsonField> predicate,
            final DittoHeaders dittoHeaders) {

        final JsonSchemaVersion schemaVersion = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        if (fieldSelector != null) {
            return predicate != null
                    ? thing.toJson(schemaVersion, fieldSelector, predicate)
                    : thing.toJson(schemaVersion, fieldSelector);
        } else {
            return predicate != null
                    ? thing.toJson(schemaVersion, predicate)
                    : thing.toJson(schemaVersion);
        }
    }

    /**
     * Returns a new instance of {@code RetrieveThingResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing.
     * @param thing the retrieved Thing or {@code null} if only {@code thingPlainJson} is available.
     * @param thingPlainJson the retrieved Thing as plain JSON string.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveThingResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code RetrieveThingResponse}.
     * @since 2.3.0
     */
    public static RetrieveThingResponse newInstance(final ThingId thingId,
            @Nullable final JsonObject thing,
            final String thingPlainJson,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveThingResponse(thingId,
                thing,
                thingPlainJson,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveThingResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThingResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the retrieved Thing.
     *
     * @return the retrieved Thing.
     */
    public Thing getThing() {
        return ThingsModelFactory.newThing(lazyLoadThingJsonObject());
    }

    @Override
    public Optional<String> getEntityPlainString() {
        return Optional.of(thingPlainJson);
    }

    @Override
    public JsonObject getEntity(final JsonSchemaVersion schemaVersion) {
        return lazyLoadThingJsonObject();
    }

    private JsonObject lazyLoadThingJsonObject() {
        if (thing == null) {
            thing = JsonObject.of(thingPlainJson);
        }
        return thing;
    }

    @Override
    public RetrieveThingResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(thingId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, thing, thingPlainJson, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_THING_PLAIN_JSON, thingPlainJson, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveThingResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveThingResponse that = (RetrieveThingResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(thing, that.thing) &&
                Objects.equals(thingPlainJson, that.thingPlainJson) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, thing, thingPlainJson);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", thing=" + thing +
                ", thingPlainJson=" + thingPlainJson + "]";
    }

}
