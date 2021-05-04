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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableInfoProvider}.
 */
public final class ImmutableInfoProviderTest {

    @Test
    public void verify() {
        final String correlationId = "theCorrelation";
        final Map<String, String> headers = DittoHeaders.newBuilder().putHeader("foo", "bar").build();
        final Instant timestamp = Instant.now().minusSeconds(138);
        final ThingId thingId = ThingId.of("the:thing");
        final String payload = "{\"138\":\"ditto\"}";

        final ConnectionMonitor.InfoProvider info = new ImmutableInfoProvider(correlationId, timestamp,
                thingId, headers, () -> payload, false);

        InfoProviderAssert.assertThat(info)
                .hasCorrelationId(correlationId)
                .hasThingId(thingId)
                .hasTimestamp(timestamp)
                .hasPayload(payload)
                .hasHeaders(headers);
    }

}
