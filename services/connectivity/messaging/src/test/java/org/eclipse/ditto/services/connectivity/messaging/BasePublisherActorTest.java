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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
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
public final class BasePublisherActorTest {

    private static final HeaderMapping HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", "{{ header:my-cor-id-important }}")
                    .set("thing-id", "{{ header:device_id }}")
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
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                        .withText("payload")
                        .build();
        final ThingModifiedEvent thingModifiedEvent =
                TestConstants.thingModified(singletonList("")).setDittoHeaders(dittoHeaders);
        final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(thingModifiedEvent,
                singletonList(target));
        final Adaptable adaptable =
                DittoProtocolAdapter.newInstance().toAdaptable(thingModifiedEvent, TopicPath.Channel.TWIN);
        final OutboundSignal.Mapped mappedOutboundSignal =
                OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

        // when
        final ExternalMessage headerMappedExternalMessage = BasePublisherActor.applyHeaderMapping(mappedOutboundSignal,
                target.getHeaderMapping().orElse(null),
                Mockito.mock(DiagnosticLoggingAdapter.class)
        );

        // then
        final Map<String, String> actualHeaders = headerMappedExternalMessage.getHeaders();
        Assertions.assertThat(actualHeaders).containsEntry("correlation-id", correlationIdImportant);
        Assertions.assertThat(actualHeaders).containsEntry("thing-id", deviceId);
        Assertions.assertThat(actualHeaders).containsEntry("eclipse", "ditto");
    }
}
