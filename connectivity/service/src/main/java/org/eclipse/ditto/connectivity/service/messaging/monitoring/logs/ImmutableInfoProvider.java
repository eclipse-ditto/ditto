/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;

/**
 * Immutable implementation fo {@link org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor.InfoProvider}.
 */
@Immutable
public final class ImmutableInfoProvider implements ConnectionMonitor.InfoProvider {

    private final String correlationId;
    private final Instant timestamp;
    @Nullable private final EntityId entityId;
    private final Map<String, String> headers;
    // a supplier to postpone getting the payload until it is really needed
    private final Supplier<String> payloadSupplier;
    private final boolean isEmpty;

    ImmutableInfoProvider(final String correlationId, final Instant timestamp,
            @Nullable final EntityId entityId, final Map<String, String> headers,
            final Supplier<String> payloadSupplier, final boolean isEmpty) {
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.entityId = entityId;
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        this.payloadSupplier = payloadSupplier;
        this.isEmpty = isEmpty;
    }

    @Override
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Nullable
    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String getPayload() {
        return payloadSupplier.get();
    }

    @Override
    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableInfoProvider that = (ImmutableInfoProvider) o;
        return Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(headers, that.headers) &&
                Objects.equals(payloadSupplier, that.payloadSupplier) &&
                isEmpty == that.isEmpty;
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, timestamp, entityId, headers, payloadSupplier, isEmpty);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "correlationId=" + correlationId +
                ", timestamp=" + timestamp +
                ", entityId=" + entityId +
                ", headers=" + headers +
                ", isEmpty=" + isEmpty +
                "]";
    }

}
