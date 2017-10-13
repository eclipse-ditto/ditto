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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which syncs a Thing without authorization. This command is sent only internally by the Ditto services, e.g.
 * search, in order to synchronize the search Things persistence.
 */
@Immutable
public final class SyncThing extends AbstractCommand<SyncThing> implements ThingSearchSudoCommand<SyncThing> {

    /**
     * Name of the command.
     */
    public static final String NAME = "syncThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_THING_ID =
            JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;

    private SyncThing(final DittoHeaders dittoHeaders, final String thingId) {
        super(TYPE, dittoHeaders);
        this.thingId = thingId;
    }

    /**
     * Creates a new {@code SyncThing}.
     *
     * @param thingId Thing ID .
     * @param dittoHeaders the command headers of the request.
     * @return a command for syncing a Thing without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SyncThing of(final String thingId, final DittoHeaders dittoHeaders) {
        checkNotNull(thingId, "ID of Thing to be synced");

        return new SyncThing(dittoHeaders, thingId);
    }

    /**
     * Creates a new {@code SyncThing} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static SyncThing fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SyncThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static SyncThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SyncThing>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JSON_THING_ID);
            return of(extractedThingId, dittoHeaders);
        });
    }

    /**
     * Returns the IDs of the Things to be retrieved by this command.
     *
     * @return a sorted unmodifiable list containing the IDs of Things to be retrieved by this command (in order how
     * they were requested).
     */
    public String getThingId() {
        return thingId;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING_ID, thingId, predicate);
    }

    @Override
    public SyncThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, dittoHeaders);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SyncThing that = (SyncThing) o;
        return Objects.equals(thingId, that.thingId);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof SyncThing);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + "]";
    }


}
