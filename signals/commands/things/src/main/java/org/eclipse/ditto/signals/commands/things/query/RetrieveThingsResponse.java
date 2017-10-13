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

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveThings} command.
 */
@Immutable
public final class RetrieveThingsResponse extends AbstractCommandResponse<RetrieveThingsResponse> implements
        ThingQueryCommandResponse<RetrieveThingsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveThings.NAME;

    static final JsonFieldDefinition<JsonArray> JSON_THINGS =
            JsonFactory.newArrayFieldDefinition("things", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final JsonArray things;

    private RetrieveThingsResponse(final HttpStatusCode statusCode, final JsonArray things,
            final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.things = checkNotNull(things, "Things");
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param things the retrieved Things.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingsResponse of(final JsonArray things, final DittoHeaders dittoHeaders) {
        return new RetrieveThingsResponse(HttpStatusCode.OK, things, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param things the retrieved Things.
     * @param predicate the predicate to apply to the things when transforming to JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingsResponse of(final List<Thing> things, final Predicate<JsonField> predicate,
            final DittoHeaders dittoHeaders) {
        return new RetrieveThingsResponse(HttpStatusCode.OK, checkNotNull(things, "Things").stream()
                .map(thing -> thing.toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST),
                        predicate))
                .collect(JsonCollectors.valuesToArray()), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param things the retrieved Things.
     * @param fieldSelector the JsonFieldSelector to apply to the passed things when transforming to JSON.
     * @param predicate the predicate to apply to the things when transforming to JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingsResponse of(final List<Thing> things, final JsonFieldSelector fieldSelector,
            final Predicate<JsonField> predicate, final DittoHeaders dittoHeaders) {
        return new RetrieveThingsResponse(HttpStatusCode.OK, checkNotNull(things, "Things").stream().map(thing -> thing
                .toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST), fieldSelector,
                        predicate))
                .collect(JsonCollectors.valuesToArray()), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThingsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThingsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveThingsResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final JsonArray thingsJsonArray = jsonObject.getValueOrThrow(JSON_THINGS);
                    return of(thingsJsonArray, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return ":_"; // no ID for retrieve of multiple things
    }

    /**
     * Returns the retrieved Things.
     *
     * @return the retrieved Things.
     */
    public List<Thing> getThings() {
        return things.stream().filter(JsonValue::isObject).map(JsonValue::asObject).map(ThingsModelFactory::newThing)
                .collect(Collectors.toList());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return things;
    }

    @Override
    public RetrieveThingsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(entity.asArray(), getDittoHeaders());
    }

    @Override
    public RetrieveThingsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(things, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty(); // no path for retrieve of multiple things
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THINGS, things, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveThingsResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveThingsResponse that = (RetrieveThingsResponse) o;
        return that.canEqual(this) && Objects.equals(things, that.things) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), things);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", things=" + things + "]";
    }

}
