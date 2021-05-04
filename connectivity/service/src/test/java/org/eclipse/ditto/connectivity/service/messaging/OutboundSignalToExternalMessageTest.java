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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.Test;

/**
 * Unit tests for functionality in {@link OutboundSignalToExternalMessage}.
 */
public final class OutboundSignalToExternalMessageTest {

    private static final HeaderMapping HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", "{{ header:my-cor-id-important }}")
                    .set("thing-id", "{{ header:device_id }}")
                    .set("connection-id", "{{ connection:id }}")
                    .set("eclipse", "ditto")
                    .build());

    @Test
    public void ensureHeadersAreMappedAsExpected() {

        // given
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("target")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .headerMapping(HEADER_MAPPING)
                .topics(Topic.TWIN_EVENTS)
                .build();

        final String correlationId = UUID.randomUUID().toString();
        final String correlationIdImportant = correlationId + "-important!";
        final String contentType = "application/json";
        final String replyTo = "reply-to-address";
        final String deviceId = "ditto:thing";
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .schemaVersion(JsonSchemaVersion.V_2)
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .contentType(contentType)
                .putHeader("device_id", deviceId)
                .putHeader("my-cor-id-important", correlationIdImportant)
                .putHeader("foo", "bar")
                .putHeader("reply-to", replyTo)
                .build();
        final ConnectionId connectionId = ConnectionId.generateRandom();
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                .withText("payload")
                .build();
        final ThingModifiedEvent thingModifiedEvent =
                TestConstants.thingModified(Collections.emptySet()).setDittoHeaders(dittoHeaders);
        final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(thingModifiedEvent,
                List.of(target));
        final Adaptable adaptable =
                DittoProtocolAdapter.newInstance().toAdaptable(thingModifiedEvent);
        final OutboundSignal.Mapped mappedOutboundSignal =
                OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
        final OutboundSignalToExternalMessage underTest =
                OutboundSignalToExternalMessage.newInstance(mappedOutboundSignal,
                        Resolvers.forOutbound(mappedOutboundSignal, connectionId),
                        target.getHeaderMapping());

        // when
        final ExternalMessage headerMappedExternalMessage = underTest.get();

        // then
        final Map<String, String> actualHeaders = headerMappedExternalMessage.getHeaders();

        assertThat(actualHeaders).contains(entry("correlation-id", correlationIdImportant),
                entry("thing-id", deviceId),
                entry("connection-id", connectionId.toString()),
                entry("eclipse", "ditto"));
    }

}
