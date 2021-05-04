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
package org.eclipse.ditto.connectivity.model.signals.events;

import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.base.model.signals.events.AbstractEventsourcedEvent;

/**
 * Abstract base class of a {@link org.eclipse.ditto.connectivity.model.Connection} related event.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractConnectivityEvent<T extends AbstractConnectivityEvent<T>> extends
        AbstractEventsourcedEvent<T> implements ConnectivityEvent<T> {

    private final ConnectionId connectionId;

    /**
     * Constructs a new {@code AbstractConnectivityEvent} object.
     *
     * @param type the type of this event.
     * @param connectionId the identifier of the connection with which this event is associated.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractConnectivityEvent(final String type,
            final ConnectionId connectionId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(type, connectionId, timestamp, dittoHeaders, metadata, revision,
                ConnectivityEvent.JsonFields.CONNECTION_ID);
        this.connectionId = connectionId;
    }

    /**
     * Returns the identifier of this event.
     *
     * @return the identifier of this event.
     */
    @Override
    public ConnectionId getEntityId() {
        return connectionId;
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
        final AbstractConnectivityEvent<?> that = (AbstractConnectivityEvent<?>) o;
        return that.canEqual(this) &&
                Objects.equals(connectionId, that.connectionId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AbstractConnectivityEvent);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(connectionId);
        return result;
    }

    @Override
    public String toString() {
        return super.toString() + ", connectionId=" + connectionId;
    }

}
