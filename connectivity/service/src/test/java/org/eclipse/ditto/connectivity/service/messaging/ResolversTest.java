/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.Test;

public final class ResolversTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

    @Test
    public void resolveTargetAddressWithEntityIdPlaceholder() {
        final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("please-verify");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(TestConstants.CORRELATION_ID)
                .putHeader("device_id", "ditto:thing")
                .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                .build();
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("hono.command.my_tenant/{{ thing:id }}")
                .originalAddress("hono.command.my_tenant/{{ thing:id }}")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .issuedAcknowledgementLabel(acknowledgementLabel)
                .topics(Topic.TWIN_EVENTS)
                .build();
        final ThingEvent<?> source =
                ThingDeleted.of(TestConstants.Things.THING_ID, 99L, Instant.now(), dittoHeaders,
                        null);
        final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(source, List.of(target));
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(Map.of())
                .withText("payload")
                .build();
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
        final OutboundSignal.Mapped mappedSignal =
                OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

        final ExpressionResolver expressionResolver = Resolvers.forOutbound(mappedSignal, CONNECTION_ID);
        final Optional<String> resolvedOptional =
                expressionResolver.resolve("hono.command.my_tenant/{{ entity:id }}").findFirst();

        assertThat(resolvedOptional).contains("hono.command.my_tenant/" + TestConstants.Things.THING_ID);
    }
}
