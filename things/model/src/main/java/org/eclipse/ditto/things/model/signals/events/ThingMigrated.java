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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

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

    private final ThingId thingId;
    private final JsonPointer path;
    private final JsonValue value;

    private ThingMigrated(final ThingId thingId,
            final JsonPointer path,
            final JsonValue value,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(TYPE, thingId, revision, timestamp, FeatureToggle.checkMergeFeatureEnabled(TYPE, dittoHeaders), metadata);
        this.thingId = checkNotNull(thingId, "thingId");
        this.path = checkNotNull(path, "path");
        this.value = checkNotNull(value, "value");
        checkSchemaVersion();
    }

    /**
     * Creates an event of a migrated thing.
     *
     * @param thingId          The thing ID.
     * @param path             The path where the changes were applied.
     * @param value            The value describing the changes that were migrated.
     * @param revision         The revision number of the thing.
     * @param timestamp        The event timestamp.
     * @param dittoHeaders     The Ditto headers.
     * @param metadata         The metadata associated with the event.
     * @return The created {@code ThingMigrated} event.
     */
    public static ThingMigrated of(final ThingId thingId,
            final JsonPointer path,
            final JsonValue value,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new ThingMigrated(thingId, path, value, revision, timestamp, dittoHeaders, metadata);
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
                    final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID));
                    final JsonPointer path = JsonPointer.of(jsonObject.getValueOrThrow(JsonFields.JSON_PATH));
                    final JsonValue value = jsonObject.getValueOrThrow(JsonFields.JSON_VALUE);

                    return of(thingId, path, value, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(ThingEvent.JsonFields.THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_PATH, path.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_VALUE, value, predicate);
    }

    @Override
    public ThingMigrated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, path, value, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MODIFY;
    }

    @Override
    public ThingMigrated setRevision(final long revision) {
        return of(thingId, path, value, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        return path;
    }

    public JsonValue getValue() {
        return value;
    }

    @Override
    public Optional<JsonValue> getEntity() {
        return Optional.of(value);
    }

    @Override
    public ThingMigrated setEntity(final JsonValue entity) {
        return of(thingId, path, entity, getRevision(), getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
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
        return that.canEqual(this) && thingId.equals(that.thingId) &&
                path.equals(that.path) &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, path, value);
    }
    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", path=" + path +
                ", value=" + value +
                "]";
    }

    /**
     * An enumeration of the JSON fields of a {@code ThingMigrated} event.
     */
    public static final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        public static final JsonFieldDefinition<String> JSON_PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<JsonValue> JSON_VALUE =
                JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

}
