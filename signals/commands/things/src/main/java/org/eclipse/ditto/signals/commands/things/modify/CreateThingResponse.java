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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
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
 * Response to a {@link CreateThing} command.
 */
@Immutable
public final class CreateThingResponse extends AbstractCommandResponse<CreateThingResponse> implements
        ThingModifyCommandResponse<CreateThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + CreateThing.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_THING =
            JsonFactory.newJsonValueFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Thing createdThing;

    private CreateThingResponse(final Thing createdThing, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.CREATED, dittoHeaders);
        this.createdThing = checkNotNull(createdThing, "created Thing");
    }

    /**
     * Returns a new {@code CreateThingResponse} for a created Thing. This corresponds to the HTTP status code
     * {@link HttpStatusCode#CREATED}.
     *
     * @param thing the created Thing.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Thing.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateThingResponse of(final Thing thing, final DittoHeaders dittoHeaders) {
        return new CreateThingResponse(thing, dittoHeaders);
    }

    /**
     * Creates a response to a {@link CreateThing} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CreateThingResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link CreateThing} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CreateThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<CreateThingResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final Thing extractedCreatedThing = jsonObject.getValue(JSON_THING)
                            .map(JsonValue::asObject)
                            .map(ThingsModelFactory::newThing)
                            .orElse(null);
                    return of(extractedCreatedThing, dittoHeaders);
                });
    }

    /**
     * Returns the created {@code Thing}.
     *
     * @return the created Thing.
     */
    public Optional<Thing> getThingCreated() {
        return Optional.of(createdThing);
    }

    @Override
    public String getThingId() {
        return createdThing.getId()
                .orElseThrow(() -> new IllegalStateException("Thing ID was not present in created Thing"));
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(createdThing.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING, createdThing.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public CreateThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(createdThing, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CreateThingResponse that = (CreateThingResponse) o;
        return that.canEqual(this) && Objects.equals(createdThing, that.createdThing) && super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof CreateThingResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), createdThing);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingCreated=" + createdThing + "]";
    }

}
