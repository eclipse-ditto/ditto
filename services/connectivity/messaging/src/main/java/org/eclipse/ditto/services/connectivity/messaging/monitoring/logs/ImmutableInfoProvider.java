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
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithThingId;

// TODO: doc & test
@Immutable
public final class ImmutableInfoProvider implements ConnectionMonitor.InfoProvider {

    private static final String FALLBACK_CORRELATION_ID = "<not-provided>";

    private final String correlationId;
    private final Instant timestamp;
    @Nullable private final String thingId;

    private ImmutableInfoProvider(final String correlationId, final Instant timestamp,
            @Nullable final String thingId) {
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.thingId = thingId;
    }

    public static ConnectionMonitor.InfoProvider forExternalMessage(final ExternalMessage externalMessage) {
        final String correlationId = extractCorrelationId(externalMessage.getHeaders());
        final Instant timestamp = Instant.now();

        return new ImmutableInfoProvider(correlationId, timestamp, null);
    }

    public static ConnectionMonitor.InfoProvider forSignal(final Signal<?> signal) {
        final String correlationId = extractCorrelationId(signal.getDittoHeaders());
        final Instant timestamp = Instant.now();
        final String thingId = extractThingId(signal);

        return new ImmutableInfoProvider(correlationId, timestamp, thingId);
    }

    public static ConnectionMonitor.InfoProvider forHeaders(final Map<String, String> headers) {
        final String correlationId = extractCorrelationId(headers);
        final Instant timestamp = Instant.now();

        return new ImmutableInfoProvider(correlationId, timestamp, null);
    }

    private static String extractCorrelationId(final Map<String, String> headers) {
        return headers.getOrDefault(DittoHeaderDefinition.CORRELATION_ID.getKey(), FALLBACK_CORRELATION_ID);
    }

    @Nullable
    private static String extractThingId(final Signal<?> signal) {
        if (signal instanceof WithThingId) {
            return ((WithThingId) signal).getThingId();
        }
        return null;
    }

    public static ConnectionMonitor.InfoProvider empty() {
        return new ImmutableInfoProvider(FALLBACK_CORRELATION_ID, Instant.now(), null);
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
    public String getThingId() {
        return thingId;
    }

}
