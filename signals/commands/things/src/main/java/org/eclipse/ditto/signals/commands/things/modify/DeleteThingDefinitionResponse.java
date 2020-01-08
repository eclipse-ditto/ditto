/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.things.modify;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;

/**
 * Response to a {@link DeleteThingDefinition} command.
 */
@Immutable
@JsonParsableCommandResponse(type = DeleteThingDefinitionResponse.TYPE)
public final class DeleteThingDefinitionResponse extends AbstractCommandResponse<DeleteThingDefinitionResponse>
        implements
        ThingModifyCommandResponse<DeleteThingDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeleteThingDefinition.NAME;

    private final ThingId thingId;

    private DeleteThingDefinitionResponse(final ThingId thingId, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.NO_CONTENT, dittoHeaders);
        this.thingId = requireNonNull(thingId, "thing ID");
    }


    /**
     * Creates a response to a {@link org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinition} command.
     *
     * @param thingId the Thing ID of the deleted definition.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static DeleteThingDefinitionResponse of(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return new DeleteThingDefinitionResponse(thingId, dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteThingDefinition} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static DeleteThingDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeleteThingDefinitionResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<DeleteThingDefinitionResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final String extractedThingId =
                            jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    return of(thingId, dittoHeaders);
                });
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("definition/");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
    }

    @Override
    public DeleteThingDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteThingDefinitionResponse that = (DeleteThingDefinitionResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof DeleteThingDefinitionResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + "]";
    }

}