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
@JsonParsableEvent(name = ThingDefinitionMigrated.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class ThingDefinitionMigrated
        extends AbstractThingEvent<ThingDefinitionMigrated> implements ThingModifiedEvent<ThingDefinitionMigrated> {

    /**
     * Name of the "Thing Definition Migrated" event.
     */
    public static final String NAME = "ThingDefinitionMigrated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Thing thing;

    private ThingDefinitionMigrated(final Thing thing,
                          final long revision,
                          @Nullable final Instant timestamp,
                          final DittoHeaders dittoHeaders,
                          @Nullable final Metadata metadata) {
        super(TYPE, thing.getEntityId().orElseThrow(() -> new IllegalArgumentException("Thing has no ID!")), revision, timestamp, dittoHeaders, metadata);
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
     * @return The created {@code ThingDefinitionMigrated} event.
     */
    public static ThingDefinitionMigrated of(final Thing thing,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new ThingDefinitionMigrated(thing, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ThingDefinitionMigrated} event from a JSON object.
     *
     * @param jsonObject   The JSON object from which the event is created.
     * @param dittoHeaders The headers of the command.
     * @return The {@code ThingDefinitionMigrated} event created from JSON.
     */
    public static ThingDefinitionMigrated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ThingDefinitionMigrated>(TYPE, jsonObject).deserialize(
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
    public ThingDefinitionMigrated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thing, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MIGRATE;
    }

    @Override
    public ThingDefinitionMigrated setRevision(final long revision) {
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
    public ThingDefinitionMigrated setEntity(final JsonValue entity) {
        return of(ThingsModelFactory.newThing(entity.asObject()), getRevision(), getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
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
        final ThingDefinitionMigrated that = (ThingDefinitionMigrated) o;
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
