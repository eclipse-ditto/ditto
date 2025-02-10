/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.events;


import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * This event is emitted after a {@link org.eclipse.ditto.things.model.Thing} was successfully migrated.
 *
 * @since 3.7.0
 */
@Immutable
@JsonParsableEvent(name = ThingMigrated.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class ThingMigrated extends AbstractThingEvent<ThingMigrated> implements ThingModifiedEvent<ThingMigrated> {

    /**
     * Name of the "Thing Migrated" event.
     */
    public static final String NAME = "thingMigrated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Thing thing;

    private ThingMigrated(final Thing thing,
                          final long revision,
                          @Nullable final Instant timestamp,
                          final DittoHeaders dittoHeaders,
                          @Nullable final Metadata metadata) {
        super(TYPE, thing.getEntityId().orElseThrow(() -> new NullPointerException("Thing has no ID!")), revision, timestamp, dittoHeaders, metadata);
        this.thing = thing;
        checkSchemaVersion();
    }

    /**
     * Creates an event of a migrated thing.
     *
     * @param thing the created {@link org.eclipse.ditto.things.model.Thing}.
     * @param revision         The revision number of the thing.
     * @param timestamp        The event timestamp.
     * @param dittoHeaders     The Ditto headers.
     * @param metadata         The metadata associated with the event.
     * @return The created {@code ThingMigrated} event.
     */
    public static ThingMigrated of(final Thing thing,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new ThingMigrated(thing, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ThingMigrated} event from a JSON object.
     *
     * @param jsonObject   The JSON object from which the event is created.
     * @param dittoHeaders The headers of the command.
     * @return The {@code ThingMigrated} event created from JSON.
     */
    public static ThingMigrated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ThingMigrated>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final JsonObject thingJsonObject = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING);
                    final Thing extractedThing = ThingsModelFactory.newThing(thingJsonObject);

                    return of(extractedThing, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingEvent.JsonFields.THING, thing.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public ThingMigrated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thing, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MIGRATE;
    }

    @Override
    public ThingMigrated setRevision(final long revision) {
        return of(thing, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    /**
     * @return the value describing the changes that were applied to the existing thing.
     */
    public Thing getThing() {
        return thing;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(thing.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public ThingMigrated setEntity(final JsonValue entity) {
        return of(ThingsModelFactory.newThing(entity.asObject()), getRevision(), getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    private void checkSchemaVersion() {
        final JsonSchemaVersion implementedSchemaVersion = getImplementedSchemaVersion();
        if (!implementsSchemaVersion(implementedSchemaVersion)) {
            throw UnsupportedSchemaVersionException.newBuilder(implementedSchemaVersion).build();
        }
    }

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
        final ThingMigrated that = (ThingMigrated) o;
        return that.canEqual(this) && thing.equals(that.thing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thing);
    }
    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", thing=" + thing +
                "]";
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

}
