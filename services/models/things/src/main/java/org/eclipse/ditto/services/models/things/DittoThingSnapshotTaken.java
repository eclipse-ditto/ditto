/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.things;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Event published when a thing snapshot is taken.
 */
@JsonParsableEvent(name = DittoThingSnapshotTaken.NAME, typePrefix = DittoThingSnapshotTaken.TYPE_PREFIX)
public final class DittoThingSnapshotTaken implements ThingSnapshotTaken, Event<DittoThingSnapshotTaken> {

    private static final String RESOURCE_TYPE = "thing";

    static final String TYPE_PREFIX = RESOURCE_TYPE + ":";

    static final String NAME = "dittoThingSnapshotTaken";

    static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_THING_ID =
            JsonFactory.newStringFieldDefinition("id", JsonSchemaVersion.V_2);

    private final ThingId thingId;

    private DittoThingSnapshotTaken(final ThingId thingId) {
        this.thingId = thingId;
    }

    /**
     * Create a new {@code DittoThingSnapshotTaken} event.
     *
     * @param thingId the ID of the snapshot thing.
     * @return the event.
     */
    public static DittoThingSnapshotTaken of(final ThingId thingId) {
        return new DittoThingSnapshotTaken(thingId);
    }

    /**
     * Creates an instance of {@code DittoThingSnapshotTaken} from the given JSON object.
     *
     * @param jsonObject the JSON representation of the event that should be instantiated.
     * @param dittoHeaders Ditto headers that won't have any effect.
     * @return the created instance of this event.
     */
    public static DittoThingSnapshotTaken fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return of(ThingId.of(jsonObject.getValueOrThrow(JSON_THING_ID)));
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public long getRevision() {
        return 0;
    }

    @Override
    public DittoThingSnapshotTaken setRevision(final long revision) {
        return this;
    }

    @Override
    public Optional<Instant> getTimestamp() {
        return Optional.empty();
    }

    @Override
    public Optional<Metadata> getMetadata() {
        return Optional.empty();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return DittoHeaders.empty();
    }

    @Override
    public DittoThingSnapshotTaken setDittoHeaders(final DittoHeaders dittoHeaders) {
        return this;
    }

    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return JsonObject.newBuilder()
                .set(JsonFields.TYPE, TYPE)
                .set(JSON_THING_ID, thingId.toString())
                .build();
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoThingSnapshotTaken that = (DittoThingSnapshotTaken) o;
        return Objects.equals(thingId, that.thingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                "]";
    }
}
