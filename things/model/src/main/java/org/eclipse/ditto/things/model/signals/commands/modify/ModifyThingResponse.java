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
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link ModifyThing} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyThingResponse.TYPE)
public final class ModifyThingResponse extends AbstractCommandResponse<ModifyThingResponse>
        implements ThingModifyCommandResponse<ModifyThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + ModifyThing.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_THING =
            JsonFieldDefinition.ofJsonValue("thing", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyThingResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValue(JSON_THING)
                                        .map(JsonValue::asObject)
                                        .map(ThingsModelFactory::newThing)
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    @Nullable private final Thing thingCreated;

    private ModifyThingResponse(final ThingId thingId,
            @Nullable final Thing thingCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.thingCreated = thingCreated;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != thingCreated) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Thing <{0}> is illegal in conjunction with <{1}>.",
                            thingCreated,
                            httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyThingResponse} for a created Thing. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thing the created Thing.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Thing.
     * @throws NullPointerException if any argument is {@code null} or if {@code thing} has no entity ID.
     */
    public static ModifyThingResponse created(final Thing thing, final DittoHeaders dittoHeaders) {
        checkNotNull(thing, "thing");
        final ThingId thingId = thing.getEntityId().orElseThrow(() -> new NullPointerException("Thing has no ID!"));
        return newInstance(thingId, thing, HttpStatus.CREATED, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyThingResponse} for a modified Thing. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified Thing.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Thing.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyThingResponse modified(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return newInstance(thingId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyThingResponse} for the specified arguments.
     *
     * @param thingId the ID of the created or modified thing.
     * @param thingCreated the created thing or {@code null} if an existing thing was modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyThingResponse} instance.
     * @throws NullPointerException if any argument but {@code thingCreated} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyThingResponse} or
     * if {@code httpStatus} conflicts with {@code thingCreated}.
     * @since 2.3.0
     */
    public static ModifyThingResponse newInstance(final ThingId thingId,
            @Nullable final Thing thingCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyThingResponse(thingId,
                thingCreated,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyThingResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyThing} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyThingResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyThing} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the created {@code Thing}.
     *
     * @return the created Thing.
     */
    public Optional<Thing> getThingCreated() {
        return Optional.ofNullable(thingCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(thingCreated).map(thing -> thing.toJson(schemaVersion, FieldType.notHidden()));
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
        if (null != thingCreated) {
            jsonObjectBuilder.set(JSON_THING, thingCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, thingCreated, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyThingResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyThingResponse that = (ModifyThingResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(thingCreated, that.thingCreated) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, thingCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", thingCreated=" +
                thingCreated + "]";
    }

}
