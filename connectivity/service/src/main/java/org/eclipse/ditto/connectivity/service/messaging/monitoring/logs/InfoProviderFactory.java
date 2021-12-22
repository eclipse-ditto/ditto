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

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.messaging.monitoring.logs.LogEntryFactory;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;

/**
 * Factory for creating instances of {@link org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor.InfoProvider}.
 */
public final class InfoProviderFactory {

    public static final String FALLBACK_CORRELATION_ID = LogEntryFactory.FALLBACK_CORRELATION_ID;

    private InfoProviderFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new info provider that uses {@code externalMessage} for getting a correlation ID.
     *
     * @param externalMessage the external message that might contain a correlation ID.
     * @return an info provider with a correlation ID.
     */
    public static ConnectionMonitor.InfoProvider forExternalMessage(final ExternalMessage externalMessage) {
        final String correlationId = extractCorrelationId(externalMessage.getHeaders());
        final Instant timestamp = Instant.now();
        final Supplier<String> payloadSupplier = supplyPayloadFromExternalMessage(externalMessage);

        return new ImmutableInfoProvider(correlationId, timestamp, null, externalMessage.getHeaders(), payloadSupplier,
                false);
    }

    private static Supplier<String> supplyPayloadFromExternalMessage(final ExternalMessage externalMessage) {
        if (externalMessage.isTextMessage()) {
            return () -> externalMessage.getTextPayload().orElse("<empty-text-payload>");
        }
        return () -> externalMessage.getBytePayload()
                .filter(ByteBuffer::hasArray)
                .map(ByteBuffer::array)
                .map(Base64.getEncoder()::encodeToString)
                .orElse("<empty-byte-payload>");
    }

    /**
     * Creates a new info provider that uses {@code signal} for getting a correlation ID and thing ID.
     *
     * @param signal the signal that might contain a correlation ID and thing ID.
     * @return an info provider with a correlation ID and maybe a thing ID.
     */
    public static ConnectionMonitor.InfoProvider forSignal(final Signal<?> signal) {
        final String correlationId = extractCorrelationId(signal.getDittoHeaders());
        final Instant timestamp = Instant.now();
        final EntityId entityId = extractEntityId(signal);
        final Supplier<String> payloadSupplier = supplyPayloadFromSignal(signal);

        return new ImmutableInfoProvider(correlationId, timestamp, entityId, signal.getDittoHeaders(), payloadSupplier,
                false);
    }

    private static Supplier<String> supplyPayloadFromSignal(final Signal<?> signal) {
        return signal::toJsonString;

    }

    /**
     * Creates a new info provider that uses {@code headers} for getting a correlation ID.
     *
     * @param headers the headers that might contain a correlation ID.
     * @return an info provider with a correlation ID.
     */
    public static ConnectionMonitor.InfoProvider forHeaders(final Map<String, String> headers) {
        final String correlationId = extractCorrelationId(headers);
        final Instant timestamp = Instant.now();

        return new ImmutableInfoProvider(correlationId, timestamp, null, headers, supplyEmptyPayload(), false);
    }

    private static String extractCorrelationId(final Map<String, String> headers) {
        return headers.getOrDefault(
                DittoHeaderDefinition.CORRELATION_ID.getKey(), FALLBACK_CORRELATION_ID);
    }

    @Nullable
    private static EntityId extractEntityId(final Signal<?> signal) {
        return WithEntityId.getEntityIdOfType(EntityId.class, signal).orElse(null);
    }

    public static ConnectionMonitor.InfoProvider empty() {
        return new ImmutableInfoProvider(FALLBACK_CORRELATION_ID, Instant.now(), null, Collections.emptyMap(),
                supplyEmptyPayload(), true);
    }

    private static Supplier<String> supplyEmptyPayload() {
        return () -> null;
    }

}
