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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Map;

import javax.annotation.Nullable;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableInfoProvider}.
 */
public final class ImmutableInfoProviderTest {

    @Test
    public void forExternalMessage() {
        final String correlationId = "theCorrelation";
        final Map<String, String> headersWithCorrelationId = DittoHeaders.newBuilder().correlationId(correlationId).build();
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headersWithCorrelationId)
                .build();

        final Instant before = Instant.now();
        final ConnectionMonitor.InfoProvider info = ImmutableInfoProvider.forExternalMessage(externalMessage);
        final Instant after = Instant.now();

        assertThat(info)
                .hasNoThingId()
                .hasCorrelationId(correlationId)
                .hasTimestampBetween(before, after);
    }

    @Test
    public void forSignal() {
        final String thingId = "the:thing";
        final String correlationId = "theCorrelation";
        final DittoHeaders headersWithCorrelationId = DittoHeaders.newBuilder().correlationId(correlationId).build();
        final Signal<?> signal = RetrieveThing.of(thingId, headersWithCorrelationId);

        final Instant before = Instant.now();
        final ConnectionMonitor.InfoProvider info = ImmutableInfoProvider.forSignal(signal);
        final Instant after = Instant.now();

        assertThat(info)
                .hasThingId(thingId)
                .hasCorrelationId(correlationId)
                .hasTimestampBetween(before, after);
    }

    @Test
    public void forHeaders() {
        final String correlationId = "theCorrelation";
        final Map<String, String> headersWithCorrelationId = DittoHeaders.newBuilder().correlationId(correlationId).build();

        final Instant before = Instant.now();
        final ConnectionMonitor.InfoProvider info = ImmutableInfoProvider.forHeaders(headersWithCorrelationId);
        final Instant after = Instant.now();

        assertThat(info)
                .hasNoThingId()
                .hasCorrelationId(correlationId)
                .hasTimestampBetween(before, after);
    }

    @Test
    public void empty() {
        final Instant before = Instant.now();
        final ConnectionMonitor.InfoProvider info = ImmutableInfoProvider.empty();
        final Instant after = Instant.now();

        assertThat(info)
                .hasNoThingId()
                .hasDefaultCorrelationId()
                .hasTimestampBetween(before, after);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(
                ImmutableInfoProvider.class).verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(ImmutableInfoProvider.class,
                areImmutable());
    }

    private InfoProviderAssert assertThat(final ConnectionMonitor.InfoProvider infoProvider) {
        return new InfoProviderAssert(infoProvider);
    }

    private static class InfoProviderAssert extends AbstractAssert<InfoProviderAssert, ConnectionMonitor.InfoProvider> {

        private InfoProviderAssert(final ConnectionMonitor.InfoProvider infoProvider) {
            super(infoProvider, InfoProviderAssert.class);
        }

        private InfoProviderAssert hasThingId(@Nullable final String thingId) {
            isNotNull();
            Assertions.assertThat(actual.getThingId()).isEqualTo(thingId);
            return this;
        }

        private InfoProviderAssert hasNoThingId() {
            return hasThingId(null);
        }

        private InfoProviderAssert hasTimestampBetween(final Instant startInclusive, final Instant endInclusive) {
            isNotNull();
            Assertions.assertThat(actual.getTimestamp()).isBetween(startInclusive, endInclusive);
            return this;
        }

        private InfoProviderAssert hasCorrelationId(final String correlationId) {
            isNotNull();
            Assertions.assertThat(actual.getCorrelationId()).isEqualTo(correlationId);
            return this;
        }

        private InfoProviderAssert hasDefaultCorrelationId() {
            return hasCorrelationId("<not-provided>");
        }

    }

}
