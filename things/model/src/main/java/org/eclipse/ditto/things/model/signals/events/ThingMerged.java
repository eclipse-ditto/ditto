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
 * This event is emitted after a {@link org.eclipse.ditto.things.model.Thing} was merged.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableEvent(name = ThingMerged.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class ThingMerged extends AbstractThingEvent<ThingMerged> implements ThingModifiedEvent<ThingMerged> {

    /**
     * Name of the "Thing Merged" event.
     */
    public static final String NAME = "thingMerged";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ThingId thingId;
    private final JsonPointer path;
    private final JsonValue value;

    private ThingMerged(final ThingId thingId,
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
     * Creates an event of merged thing.
     *
     * @param thingId the thing id.
     * @param path the path where the changes were applied.
     * @param value the value describing the changes that were merged into the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@code ThingMerged} event.
     */
    public static ThingMerged of(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final long revision, @Nullable final Instant timestamp, final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new ThingMerged(thingId, path, value, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ThingMerged} event from a JSON object.
     *
     * @param jsonObject the JSON object of which the event is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the {@code ThingMerged} event created from JSON.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a field for
     * {@link org.eclipse.ditto.things.model.signals.events.ThingEvent.JsonFields#THING_ID}, {@link ThingMerged.JsonFields#JSON_PATH} or {@link ThingMerged.JsonFields#JSON_VALUE}.
     */
    public static ThingMerged fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ThingMerged>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID));
                    final JsonPointer path = JsonPointer.of(jsonObject.getValueOrThrow(JsonFields.JSON_PATH));
                    final JsonValue value = jsonObject.getValueOrThrow(JsonFields.JSON_VALUE);
                    return of(thingId, path, value, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingEvent.JsonFields.THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_PATH, path.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_VALUE, value, predicate);
    }

    @Override
    public ThingMerged setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, path, value, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MERGE;
    }

    @Override
    public ThingMerged setRevision(final long revision) {
        return of(thingId, path, value, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    /**
     * @return the path where the changes were applied.
     */
    @Override
    public JsonPointer getResourcePath() {
        return path;
    }

    /**
     * @return the value describing the changes that were applied to the existing thing.
     */
    public JsonValue getValue() {
        return value;
    }

    @Override
    public Optional<JsonValue> getEntity() {
        return Optional.of(value);
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
        final ThingMerged that = (ThingMerged) o;
        return that.canEqual(this) && thingId.equals(that.thingId) &&
                path.equals(that.path) &&
                value.equals(that.value);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ThingMerged;
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
     * An enumeration of the JSON fields of a {@code ThingMerged} event.
     */
    static final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        static final JsonFieldDefinition<String> JSON_PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonValue> JSON_VALUE =
                JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_2);
    }
}
