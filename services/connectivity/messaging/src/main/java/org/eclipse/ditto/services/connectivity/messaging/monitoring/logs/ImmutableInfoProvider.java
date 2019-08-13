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

package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;

/**
 * Immutable implementation fo {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor.InfoProvider}.
 */
@Immutable
public final class ImmutableInfoProvider implements ConnectionMonitor.InfoProvider {

    private final String correlationId;
    private final Instant timestamp;
    @Nullable private final ThingId thingId;
    private final Map<String, String> headers;
    // a supplier to postpone getting the payload until it is really needed
    private final Supplier<String> payloadSupplier;

    ImmutableInfoProvider(final String correlationId, final Instant timestamp,
            @Nullable final ThingId thingId, final Map<String, String> headers, final Supplier<String> payloadSupplier) {
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.thingId = thingId;
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        this.payloadSupplier = payloadSupplier;
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
    public ThingId getThingId() {
        return thingId;
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
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(headers, that.headers) &&
                Objects.equals(payloadSupplier, that.payloadSupplier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, timestamp, thingId, headers, payloadSupplier);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", correlationId=" + correlationId +
                ", timestamp=" + timestamp +
                ", thingId=" + thingId +
                ", headers=" + headers +
                "]";
    }

}
