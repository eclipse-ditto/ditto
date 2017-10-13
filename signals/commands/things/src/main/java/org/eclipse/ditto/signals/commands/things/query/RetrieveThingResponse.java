/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveThing} command.
 */
@Immutable
public final class RetrieveThingResponse extends AbstractCommandResponse<RetrieveThingResponse> implements
        ThingQueryCommandResponse<RetrieveThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveThing.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final JsonObject thing;

    private RetrieveThingResponse(final String thingId, final HttpStatusCode statusCode, final JsonObject thing,
            final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thing ID");
        this.thing = checkNotNull(thing, "Thing");
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
    public static RetrieveThingResponse of(final String thingId, final JsonObject thing,
            final DittoHeaders dittoHeaders) {
        return new RetrieveThingResponse(thingId, HttpStatusCode.OK, thing, dittoHeaders);
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
    public static RetrieveThingResponse of(final String thingId, final Thing thing,
            final DittoHeaders dittoHeaders) {
        return new RetrieveThingResponse(thingId, HttpStatusCode.OK,
                checkNotNull(thing, "Thing").toJson(
                        dittoHeaders.getSchemaVersion().orElse(thing.getLatestSchemaVersion())),
                dittoHeaders);
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
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingQueryCommandResponse.JsonFields.JSON_THING_ID);
                    final JsonObject extractedThing = jsonObject.getValueOrThrow(JSON_THING);

                    return of(thingId, extractedThing, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the retrieved Thing.
     *
     * @return the retrieved Thing.
     */
    public Thing getThing() {
        return ThingsModelFactory.newThing(thing);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return thing;
    }

    @Override
    public RetrieveThingResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(thingId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, thing, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_THING, thing, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveThingResponse);
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
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && Objects.equals(thing, that.thing) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, thing);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", thing=" + thing + "]";
    }

}
