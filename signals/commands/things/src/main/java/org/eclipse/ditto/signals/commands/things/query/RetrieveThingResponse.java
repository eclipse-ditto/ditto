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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveThing} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveThingResponse.TYPE)
public final class RetrieveThingResponse extends AbstractCommandResponse<RetrieveThingResponse> implements
        ThingQueryCommandResponse<RetrieveThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveThing.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_THING_PLAIN_JSON =
            JsonFactory.newStringFieldDefinition("thingPlainJson", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final String thingPlainJson;

    @Nullable private JsonObject thing;

    private RetrieveThingResponse(final ThingId thingId, final HttpStatusCode statusCode,
            @Nullable final JsonObject thing, final String thingPlainJson, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thing ID");
        this.thingPlainJson = checkNotNull(thingPlainJson, "Thing plain JSON");
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
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, org.eclipse.ditto.json.JsonObject, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static RetrieveThingResponse of(final String thingId, final JsonObject thing,
            final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), thing, dittoHeaders);
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
    public static RetrieveThingResponse of(final ThingId thingId, final JsonObject thing,
            final DittoHeaders dittoHeaders) {
        return new RetrieveThingResponse(thingId, HttpStatusCode.OK, thing, thing.toString(), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command.
     *
     * @param thingId the Thing ID of the retrieved Thing.
     * @param thingPlainJson the retrieved Thing as plain JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, String, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static RetrieveThingResponse of(final String thingId, final String thingPlainJson,
            final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), thingPlainJson, dittoHeaders);
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
        return new RetrieveThingResponse(thingId, HttpStatusCode.OK, null, thingPlainJson, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command.
     *
     * @param thingId the Thing ID of the retrieved Thing.
     * @param thing the retrieved Thing.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, org.eclipse.ditto.model.things.Thing, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static RetrieveThingResponse of(final String thingId, final Thing thing,
            final DittoHeaders dittoHeaders) {

        return of(ThingId.of(thingId), thing, dittoHeaders);
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
    public static RetrieveThingResponse of(final ThingId thingId, final Thing thing,
            final DittoHeaders dittoHeaders) {

        final JsonObject thingJson = checkNotNull(thing, "Thing")
                .toJson(dittoHeaders.getSchemaVersion().orElse(thing.getLatestSchemaVersion()));
        return new RetrieveThingResponse(thingId, HttpStatusCode.OK, thingJson, thingJson.toString(), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThingResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveThingResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final String extractedThingId =
                            jsonObject.getValueOrThrow(ThingQueryCommandResponse.JsonFields.JSON_THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final JsonObject extractedThing = jsonObject.getValue(JSON_THING).orElse(null);
                    final String extractedThingPlainJson = jsonObject.getValue(JSON_THING_PLAIN_JSON)
                            .orElseGet(() -> {
                                if (null == extractedThing) {
                                    throw JsonMissingFieldException.newBuilder()
                                            .fieldName(JSON_THING.getPointer())
                                            .build();
                                }
                                return extractedThing.toString();
                            });

                    return new RetrieveThingResponse(thingId, statusCode, extractedThing, extractedThingPlainJson,
                            dittoHeaders);
                });
    }

    @Override
    public ThingId getThingEntityId() {
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
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return lazyLoadThingJsonObject();
    }

    private JsonObject lazyLoadThingJsonObject() {
        if (thing == null) {
            thing = JsonFactory.readFrom(thingPlainJson).asObject();
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
        return of(thingId, thingPlainJson, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_THING_PLAIN_JSON, thingPlainJson, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveThingResponse;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveThingResponse that = (RetrieveThingResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && Objects.equals(thing, that.thing)
                && Objects.equals(thingPlainJson, that.thingPlainJson) &&
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
