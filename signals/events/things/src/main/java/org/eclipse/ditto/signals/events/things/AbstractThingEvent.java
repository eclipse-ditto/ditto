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
package org.eclipse.ditto.signals.events.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;


/**
 * Abstract base class of an event store event.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractThingEvent<T extends AbstractThingEvent> implements ThingEvent<T> {

    private final String type;
    private final String thingId;
    private final long revision;
    @Nullable private final Instant timestamp;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractThingEvent} object.
     *
     * @param type the type of this event.
     * @param thingId the ID of the Thing with which this event is associated.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    protected AbstractThingEvent(final String type,
            final String thingId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        this.type = checkNotNull(type, "Event type");
        this.thingId = checkNotNull(thingId, "Thing identifier");
        this.revision = revision;
        this.timestamp = timestamp;
        this.dittoHeaders = checkNotNull(dittoHeaders, "command headers");
    }

    @Override
    public String getType() {
        return type;
    }

    /**
     * Returns the identifier of this event's {@code Thing}.
     *
     * @return the identifier of this event's Thing.
     */
    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public Optional<Instant> getTimestamp() {
        return Optional.ofNullable(timestamp);
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                // TYPE is included unconditionally
                .set(Event.JsonFields.TYPE, type)
                .set(Event.JsonFields.TIMESTAMP, getTimestamp().map(Instant::toString).orElse(null), predicate)
                .set(Event.JsonFields.REVISION, revision, predicate)
                .set(JsonFields.THING_ID, thingId);

        appendPayloadAndBuild(jsonObjectBuilder, schemaVersion, thePredicate);

        return jsonObjectBuilder.build();
    }

    /**
     * Appends the event specific custom payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the custom payload to.
     * @param schemaVersion the JsonSchemaVersion used in toJson().
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate);

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractThingEvent that = (AbstractThingEvent) o;
        return that.canEqual(this) &&
                Objects.equals(type, that.type) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(revision, that.revision) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(dittoHeaders, that.dittoHeaders);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AbstractThingEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, thingId, revision, timestamp, dittoHeaders);
    }

    @Override
    public String toString() {
        return "type=" + type + ", thingId=" + thingId + ", revision=" + revision + ", timestamp=" + timestamp + ", " +
                "dittoHeaders=" + dittoHeaders;
    }

}
