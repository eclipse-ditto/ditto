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
package org.eclipse.ditto.services.connectivity.messaging;

import static java.util.Collections.singletonList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit tests for functionality in {@link BasePublisherActor}.
 */
public class BasePublisherActorTest {

    private static Map<String, String> headerMappingMap = new HashMap<>();
    static {
        headerMappingMap.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), "{{ header:my-cor-id-important }}");
        headerMappingMap.put("thing-id", "{{ header:device_id }}");
        headerMappingMap.put("eclipse", "ditto");
    }

    private static final HeaderMapping HEADER_MAPPING = ConnectivityModelFactory.newHeaderMapping(headerMappingMap);

    @Test
    public void ensureHeadersAreMappedAsExpected() {

        // given
        final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singletonList(""));
        final Target target =
                ConnectivityModelFactory.newTarget("target", TestConstants.Authorization.AUTHORIZATION_CONTEXT,
                        HEADER_MAPPING, null,
                        Topic.TWIN_EVENTS);
        final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(thingModifiedEvent,
                singletonList(target));

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
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(dittoHeaders)
                        .withText("payload")
                        .build();
        final OutboundSignal.WithExternalMessage mappedOutboundSignal =
                OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, externalMessage);

        // when
        final ExternalMessage headerMappedExternalMessage = BasePublisherActor.applyHeaderMapping(mappedOutboundSignal, target,
                Mockito.mock(DiagnosticLoggingAdapter.class));

        // then
        final Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType); // content-type must be always preserved
        expectedHeaders.put("reply-to", replyTo); // reply-to must be always preserved
        expectedHeaders.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationIdImportant); // the overwritten correlation-id from the headerMapping
        expectedHeaders.put("thing-id", deviceId); // as defined in headerMappingMap
        expectedHeaders.put("eclipse", "ditto"); // as defined in headerMappingMap

        final Map<String, String> actualHeaders = headerMappedExternalMessage.getHeaders();
        Assertions.assertThat(actualHeaders).containsOnly(expectedHeaders.entrySet().toArray(new Map.Entry[0]));
    }
}
