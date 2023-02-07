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
package org.eclipse.ditto.base.model.signals.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Abstract base class of an event store event.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractEventsourcedEvent<T extends AbstractEventsourcedEvent<T>> extends AbstractEvent<T>
        implements EventsourcedEvent<T> {

    private final EntityId entityId;
    private final long revision;
    private final JsonFieldDefinition<String> entityIdFieldDefinition;

    /**
     * Constructs a new {@code AbstractEvent} object.
     *
     * @param type the type of this event.
     * @param entityId the ID of the entity with which this eventsourced event is associated.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata which was applied together with the event, relative to the event's
     * {@link #getResourcePath()}.
     * @param revision the revision of the Entity derived from the eventsource persistence.
     * @param entityIdFieldDefinition the field definition of the specific entityId of this eventsourced event.
     * @throws NullPointerException if any argument but {@code timestamp} or {@code metadata} is {@code null}.
     */
    protected AbstractEventsourcedEvent(final String type,
            final EntityId entityId,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata,
            final long revision,
            final JsonFieldDefinition<String> entityIdFieldDefinition) {

        super(type, timestamp, dittoHeaders, metadata);
        this.entityId = checkNotNull(entityId, "entityId");
        this.revision = revision;
        this.entityIdFieldDefinition = entityIdFieldDefinition;
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    /**
     * Return a new immutable copy of this event with the given {@code revision}.
     * NOT part of the interface as this is only required for tests.
     *
     * @param revision the event's revision.
     * @return the copy of the event with the given revision.
     */
    public abstract T setRevision(long revision);

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        // Important! this method completely overwrites/replaces AbstractEvent.toJson(...)
        //  it shall not invoke super.toJson(...) because in that case "appendPayloadAndBuild" would be invoked twice
        //  and the order of the fields to appear in the JSON would not be controllable!
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                // TYPE + entityId is included unconditionally:
                .set(Event.JsonFields.TYPE, getType())
                .set(Event.JsonFields.TIMESTAMP, getTimestamp().map(Instant::toString).orElse(null), predicate)
                .set(Event.JsonFields.METADATA, getMetadata().map(Metadata::toJson).orElse(null), predicate)
                .set(EventsourcedEvent.JsonFields.REVISION, revision, predicate)
                .set(entityIdFieldDefinition, entityId.toString());

        appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        return jsonObjectBuilder.build();
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AbstractEventsourcedEvent<?> that = (AbstractEventsourcedEvent<?>) o;
        return that.canEqual(this) &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(revision, that.revision) &&
                Objects.equals(entityIdFieldDefinition, that.entityIdFieldDefinition);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AbstractEventsourcedEvent);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(entityId);
        result = prime * result + Objects.hashCode(revision);
        result = prime * result + Objects.hashCode(entityIdFieldDefinition);
        return result;
    }

    @Override
    public String toString() {
        return super.toString()
                + ", entityId=" + entityId
                + ", revision=" + revision;
    }

}
