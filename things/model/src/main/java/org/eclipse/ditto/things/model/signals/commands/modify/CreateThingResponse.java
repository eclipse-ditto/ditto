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
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Response to a {@link CreateThing} command.
 */
@Immutable
@JsonParsableCommandResponse(type = CreateThingResponse.TYPE)
public final class CreateThingResponse extends AbstractCommandResponse<CreateThingResponse>
        implements ThingModifyCommandResponse<CreateThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + CreateThing.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_THING =
            JsonFieldDefinition.ofJsonValue("thing", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.CREATED;

    private static final CommandResponseJsonDeserializer<CreateThingResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                jsonObject.getValue(JSON_THING)
                                        .map(JsonValue::asObject)
                                        .map(ThingsModelFactory::newThing)
                                        .orElseThrow(() -> new JsonParseException(MessageFormat.format(
                                                "JSON object <{0}> does not represent a Thing!",
                                                jsonObject)
                                        )),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final Thing createdThing;

    private CreateThingResponse(final Thing createdThing,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.createdThing = checkNotNull(createdThing, "createdThing");
    }

    /**
     * Returns a new {@code CreateThingResponse} for a created Thing. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thing the created Thing.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Thing.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateThingResponse of(final Thing thing, final DittoHeaders dittoHeaders) {
        return newInstance(thing, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code CreateThingResponse} for the specified arguments.
     *
     * @param createdThing the created thing.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code CreateThingResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code CreateThingResponse}.
     * @since 2.3.0
     */
    public static CreateThingResponse newInstance(final Thing createdThing,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new CreateThingResponse(createdThing,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        CreateThingResponse.class),
                dittoHeaders);
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
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
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
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    public ThingId getEntityId() {
        return createdThing.getEntityId()
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING, createdThing.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public CreateThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(createdThing, getHttpStatus(), dittoHeaders);
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
        return other instanceof CreateThingResponse;
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
