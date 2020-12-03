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
package org.eclipse.ditto.signals.events.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
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
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link org.eclipse.ditto.model.things.Thing} was merged.
 *
 * @since TODO replace-with-correct-version
 */
@Immutable
@JsonParsableEvent(name = ThingMerged.NAME, typePrefix = ThingMerged.TYPE_PREFIX)
public class ThingMerged extends AbstractThingEvent<ThingMerged>
        implements ThingModifiedEvent<ThingMerged> {

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

    private ThingMerged(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final long revision,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
        this.thingId = checkNotNull(thingId, "thingId");
        this.path = checkNotNull(path, "path");
        this.value = checkNotNull(value, "value");
    }

    public static ThingMerged of(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final long revision, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders, @Nullable final Metadata metadata) {
        return new ThingMerged(thingId, path, value, revision, timestamp, dittoHeaders, metadata);
    }

    public static ThingMerged fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ThingMerged>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String thingId = jsonObject.getValueOrThrow(JsonFields.JSON_THING_ID);
                    final String path = jsonObject.getValueOrThrow(JsonFields.JSON_PATH);
                    final JsonValue value = jsonObject.getValueOrThrow(JsonFields.JSON_VALUE);

                    return of(ThingId.of(thingId),JsonPointer.of(path), value, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_PATH, path.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_VALUE, value, predicate);
    }

    @Override
    public ThingMerged setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, path, value, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public ThingMerged setRevision(final long revision) {
        return of(thingId, path, value, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final ThingMerged that = (ThingMerged) o;
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
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", path=" + path +
                ", value=" + value +
                "]";
    }

    /**
     * TODO javadoc
     */
    static class JsonFields {

        static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> JSON_PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonValue> JSON_VALUE =
                JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }
}
