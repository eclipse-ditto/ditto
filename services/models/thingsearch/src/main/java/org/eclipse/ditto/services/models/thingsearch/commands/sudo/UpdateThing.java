/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.base.SignalWithEntityId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command to trigger search index update for a thing.
 * Currently a Ditto-internal message, but could become public API at some point.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = UpdateThing.TYPE_PREFIX, name = UpdateThing.NAME)
// When making this a ThingSearchCommand, beware that it is WithId but actually yes.
public final class UpdateThing extends AbstractCommand<UpdateThing> implements SignalWithEntityId<UpdateThing> {

    /**
     * Prefix for the type of this command.
     */
    public static final String TYPE_PREFIX = ThingSearchCommand.TYPE_PREFIX;

    /**
     * Name of this command.
     */
    public static final String NAME = "updateThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<String> JSON_THING_ID = Thing.JsonFields.ID;

    private final ThingId thingId;

    private UpdateThing(final ThingId thingId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingId = thingId;
    }

    /**
     * Create an UpdateThing command.
     *
     * @param thingId the ID of the thing whose search index should be updated.
     * @param dittoHeaders Ditto headers of the command.
     * @return the command.
     */
    public static UpdateThing of(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return new UpdateThing(thingId, dittoHeaders);
    }

    /**
     * Creates a new {@code UpdateThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a value for
     * "thingId".
     */
    public static UpdateThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return of(ThingId.of(jsonObject.getValueOrThrow(JSON_THING_ID)), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(JSON_THING_ID, thingId.toString(), predicate);
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public UpdateThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new UpdateThing(thingId, dittoHeaders);
    }

    @Override
    public EntityId getEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return ThingSearchCommand.RESOURCE_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof UpdateThing)) {
            return false;
        } else {
            final UpdateThing that = (UpdateThing) o;
            return Objects.equals(thingId, that.thingId) && super.equals(that);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + ",thingId=" + thingId + "]";
    }
}
