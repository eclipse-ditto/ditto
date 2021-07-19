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
import java.util.Map;

import javax.annotation.Nullable;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;

/**
 * Assertions for {@link org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor.InfoProvider}.
 */
class InfoProviderAssert extends AbstractAssert<InfoProviderAssert, ConnectionMonitor.InfoProvider> {

    private InfoProviderAssert(final ConnectionMonitor.InfoProvider infoProvider) {
        super(infoProvider, InfoProviderAssert.class);
    }


    static InfoProviderAssert assertThat(final ConnectionMonitor.InfoProvider infoProvider) {
        return new InfoProviderAssert(infoProvider);
    }

    InfoProviderAssert hasThingId(@Nullable final CharSequence thingId) {
        isNotNull();
        Assertions.assertThat((CharSequence) actual.getEntityId()).isEqualTo(thingId);
        return this;
    }

    InfoProviderAssert hasNoThingId() {
        return hasThingId(null);
    }

    InfoProviderAssert hasTimestampBetween(final Instant startInclusive, final Instant endInclusive) {
        isNotNull();
        Assertions.assertThat(actual.getTimestamp()).isBetween(startInclusive, endInclusive);
        return this;
    }

    InfoProviderAssert hasCorrelationId(final String correlationId) {
        isNotNull();
        Assertions.assertThat(actual.getCorrelationId()).isEqualTo(correlationId);
        return this;
    }

    InfoProviderAssert hasDefaultCorrelationId() {
        return hasCorrelationId("<not-provided>");
    }

    InfoProviderAssert hasHeaders(final Map<String, String> headers) {
        isNotNull();
        Assertions.assertThat(actual.getHeaders()).isEqualTo(headers);
        return this;
    }

    InfoProviderAssert hasPayload(final String payload) {
        isNotNull();
        Assertions.assertThat(actual.getPayload()).isEqualTo(payload);
        return this;
    }

    InfoProviderAssert hasEmptyTextPayload() {
        isNotNull();
        Assertions.assertThat(actual.getPayload()).isEqualTo("<empty-text-payload>");
        return this;
    }

    InfoProviderAssert hasEmptyBytePayload() {
        isNotNull();
        Assertions.assertThat(actual.getPayload()).isEqualTo("<empty-byte-payload>");
        return this;
    }

    InfoProviderAssert hasTimestamp(final Instant timestamp) {
        isNotNull();
        Assertions.assertThat(actual.getTimestamp()).isEqualTo(timestamp);
        return this;
    }

}
