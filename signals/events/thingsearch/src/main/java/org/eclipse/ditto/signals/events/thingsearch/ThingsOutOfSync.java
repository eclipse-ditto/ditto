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
package org.eclipse.ditto.signals.events.thingsearch;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithIdButActuallyNot;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Event to report out-of-sync things in the search index.
 */
@Immutable
@JsonParsableEvent(name = ThingsOutOfSync.NAME, typePrefix = ThingsOutOfSync.TYPE_PREFIX)
public final class ThingsOutOfSync implements Event<ThingsOutOfSync>, WithIdButActuallyNot {

    private static final String RESOURCE_TYPE = "thing-search";

    /**
     * Prefix for the type of this command.
     */
    public static final String TYPE_PREFIX = RESOURCE_TYPE + ".events:";

    /**
     * Name of this command.
     */
    public static final String NAME = "updateThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<JsonArray> JSON_THING_IDS =
            JsonFactory.newJsonArrayFieldDefinition("thingIds", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Collection<NamespacedEntityId> thingIds;
    private final DittoHeaders dittoHeaders;

    private ThingsOutOfSync(final Collection<NamespacedEntityId> thingIds, final DittoHeaders dittoHeaders) {
        this.thingIds = thingIds;
        this.dittoHeaders = dittoHeaders;
    }

    /**
     * Create an UpdateThings command.
     *
     * @param thingIds the IDs of the things whose search index should be updated.
     * @param dittoHeaders Ditto headers of the command.
     * @return the command.
     */
    public static ThingsOutOfSync of(final Collection<NamespacedEntityId> thingIds, final DittoHeaders dittoHeaders) {
        return new ThingsOutOfSync(thingIds, dittoHeaders);
    }

    /**
     * Creates a new {@code UpdateThings} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a value for
     * "thingId".
     */
    public static ThingsOutOfSync fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Collection<NamespacedEntityId> thingIds =
                jsonObject.getValueOrThrow(JSON_THING_IDS)
                        .stream()
                        .map(JsonValue::asString)
                        .map(DefaultNamespacedEntityId::of)
                        .collect(Collectors.toList());
        return of(thingIds, dittoHeaders);
    }

    /**
     * Retrieve the IDs of things whose search index update this command demands.
     *
     * @return the thing IDs.
     */
    public Collection<NamespacedEntityId> getThingIds() {
        return thingIds;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public ThingsOutOfSync setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ThingsOutOfSync(thingIds, dittoHeaders);
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
    public boolean equals(final Object o) {
        if (!(o instanceof ThingsOutOfSync)) {
            return false;
        } else {
            final ThingsOutOfSync that = (ThingsOutOfSync) o;
            return Objects.equals(thingIds, that.thingIds) &&
                    Objects.equals(dittoHeaders, that.dittoHeaders);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingIds, dittoHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[thingIds=" + thingIds +
                ",dittoHeaders=" + dittoHeaders +
                "]";
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public long getRevision() {
        return 0L; // this event has no revision
    }

    @Override
    public ThingsOutOfSync setRevision(final long revision) {
        return this; // this event has no revision
    }

    @Override
    public Optional<Instant> getTimestamp() {
        return Optional.empty(); // this event has no timestamp
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonArray thingIdsJsonArray = thingIds.stream()
                .map(NamespacedEntityId::toString)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
        return JsonFactory.newObjectBuilder()
                // TYPE is included unconditionally
                .set(Event.JsonFields.TYPE, TYPE)
                .set(JSON_THING_IDS, thingIdsJsonArray, predicate)
                .build();
    }
}
