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

import static org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderAssert.assertThat;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Test;

/**
 * Unit test for {@link InfoProviderFactory}.
 */
public final class InfoProviderFactoryTest {

    @Test
    public void forExternalMessage() {
        final String correlationId = "theCorrelation";
        final Map<String, String> headersWithCorrelationId = DittoHeaders.newBuilder().correlationId(correlationId).build();
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headersWithCorrelationId)
                .build();

        final Instant before = Instant.now();
        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forExternalMessage(externalMessage);
        final Instant after = Instant.now();

        assertThat(info)
                .hasNoThingId()
                .hasCorrelationId(correlationId)
                .hasTimestampBetween(before, after);
    }

    @Test
    public void forSignal() {
        final ThingId thingId = ThingId.of("the:thing");
        final String correlationId = "theCorrelation";
        final DittoHeaders headersWithCorrelationId = DittoHeaders.newBuilder().correlationId(correlationId).build();
        final Signal<?> signal = RetrieveThing.of(thingId, headersWithCorrelationId);

        final Instant before = Instant.now();
        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forSignal(signal);
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
        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forHeaders(headersWithCorrelationId);
        final Instant after = Instant.now();

        assertThat(info)
                .hasNoThingId()
                .hasCorrelationId(correlationId)
                .hasTimestampBetween(before, after);
    }

    @Test
    public void forExternalMessageWithHeaders() {
        final Map<String, String> headers = DittoHeaders.newBuilder()
                .putHeader("foo", "bar")
                .putHeader("138", "ditto")
                .build();
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();

        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forExternalMessage(externalMessage);

        assertThat(info).hasHeaders(headers);
    }

    @Test
    public void forExternalMessageWithTextPayload() {
        final String textPayload = "{ \"foo\":\"bar\" }";
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(new HashMap<>())
                .withText(textPayload)
                .build();

        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forExternalMessage(externalMessage);

        assertThat(info).hasPayload(textPayload);
    }

    @Test
    public void forExternalMessageWithBytePayload() {
        final byte[] bytePayload = "{ \"foo\":\"bar\" }".getBytes();
        final String expectedBase64Payload = Base64.getEncoder().encodeToString(bytePayload);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(new HashMap<>())
                .withBytes(bytePayload)
                .build();

        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forExternalMessage(externalMessage);

        assertThat(info).hasPayload(expectedBase64Payload);

    }

    @Test
    public void forExternalMessageWithoutTextPayload() {
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(new HashMap<>())
                .withText(null)
                .build();

        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forExternalMessage(externalMessage);

        assertThat(info).hasEmptyTextPayload();
    }

    @Test
    public void forExternalMessageWithoutBytePayload() {
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(new HashMap<>())
                .withBytes((byte[]) null)
                .build();

        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forExternalMessage(externalMessage);

        assertThat(info).hasEmptyBytePayload();
    }

    @Test
    public void forSignalWithHeaders() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader("foo", "bar")
                .putHeader("138", "ditto")
                .build();
        final Signal<?> signal = RetrieveThing.of(ThingId.of("the:thing"), headers);

        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forSignal(signal);

        assertThat(info).hasHeaders(headers);
    }

    @Test
    public void forSignalWithPayload() {

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader("foo", "bar")
                .putHeader("138", "ditto")
                .build();
        final Signal<?> signal = RetrieveThing.of(ThingId.of("the:thing"), headers);
        final String expectedTextPayload = signal.toJsonString();

        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.forSignal(signal);

        assertThat(info).hasPayload(expectedTextPayload);
    }

    @Test
    public void empty() {
        final Instant before = Instant.now();
        final ConnectionMonitor.InfoProvider info = InfoProviderFactory.empty();
        final Instant after = Instant.now();

        assertThat(info)
                .hasNoThingId()
                .hasDefaultCorrelationId()
                .hasTimestampBetween(before, after);
    }

}
