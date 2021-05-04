/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.signals.events.AbstractEventsourcedEvent;

/**
 * Abstract base class of an event store event.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractThingEvent<T extends AbstractThingEvent<T>> extends AbstractEventsourcedEvent<T>
        implements ThingEvent<T> {

    private final ThingId thingId;

    /**
     * Constructs a new {@code AbstractThingEvent} object.
     *
     * @param type the type of this event.
     * @param thingId the ID of the Thing with which this event is associated.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata which was applied together with the event, relative to the event's
     * {@link #getResourcePath()}.
     * @throws NullPointerException if any argument but {@code timestamp} or {@code metadata} is {@code null}.
     */
    protected AbstractThingEvent(final String type,
            final ThingId thingId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(type, thingId, timestamp, dittoHeaders, metadata, revision, ThingEvent.JsonFields.THING_ID);
        this.thingId = thingId;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @SuppressWarnings({"squid:S1067"})
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
        final AbstractThingEvent<?> that = (AbstractThingEvent<?>) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AbstractThingEvent);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(thingId);
        return result;
    }

    @Override
    public String toString() {
        return super.toString() + ", thingId=" + thingId;
    }

}
