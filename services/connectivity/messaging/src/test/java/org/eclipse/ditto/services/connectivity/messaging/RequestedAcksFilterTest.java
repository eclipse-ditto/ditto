/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.connectivity.messaging.RequestedAcksFilter.filterAcknowledgements;

import java.util.Map;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.placeholders.PlaceholderFunctionSignatureInvalidException;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.RequestedAcksFilter}.
 */
public final class RequestedAcksFilterTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

    @Test
    public void testRequestedAcknowledgementFilter() {
        // GIVEN
        final String requestedAcks = DittoHeaderDefinition.REQUESTED_ACKS.getKey();
        final AcknowledgementRequest twinPersistedAckRequest =
                AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED);
        final Signal<?> signal = DeleteThing.of(ThingId.of("thing:id"), DittoHeaders.empty());
        final Signal<?> signalWithRequestedAcks = DeleteThing.of(ThingId.of("thing:id"), DittoHeaders.newBuilder()
                .acknowledgementRequest(twinPersistedAckRequest)
                .build());
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(Map.of()).build();

        // WHEN/THEN

        final Signal<?> notFilteredSignal =
                filterAcknowledgements(signal, externalMessage, "fn:filter('2+2','ne','5')", CONNECTION_ID);
        assertThat(notFilteredSignal.getDittoHeaders()).doesNotContainKey(requestedAcks);

        final Signal<?> filteredSignal =
                filterAcknowledgements(signal, externalMessage, "fn:filter('2+2','eq','5')", CONNECTION_ID);
        assertThat(filteredSignal.getDittoHeaders()).contains(Map.entry(requestedAcks, "[]"));

        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                filterAcknowledgements(signal, externalMessage, "fn:filter('2','+','2','eq','5')", CONNECTION_ID)
        );

        final Signal<?> defaultValueSetSignal =
                filterAcknowledgements(signal, externalMessage, "fn:default('[\"twin-persisted\"]')", CONNECTION_ID);
        assertThat(defaultValueSetSignal.getDittoHeaders().getAcknowledgementRequests())
                .containsExactly(twinPersistedAckRequest);

        final Signal<?> transformedSignal =
                filterAcknowledgements(signalWithRequestedAcks, externalMessage,
                        "fn:filter('2+2','eq','5')|fn:default('[\"custom\"]')",
                        CONNECTION_ID);
        assertThat(transformedSignal.getDittoHeaders().getAcknowledgementRequests())
                .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest("custom"));
    }

    @Test
    public void filterByExternalHeader() {
        // GIVEN
        final String requestedAcks = DittoHeaderDefinition.REQUESTED_ACKS.getKey();
        final AcknowledgementRequest twinPersistedAckRequest =
                AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED);
        final Signal<?> signal = DeleteThing.of(ThingId.of("thing:id"), DittoHeaders.empty());
        final Map<String, String> externalHeaders = Map.of("qos", "0");
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(externalHeaders).build();

        // WHEN
        final Signal<?> filteredSignal =
                filterAcknowledgements(signal, externalMessage, "fn:filter(header:qos,'ne','0')", CONNECTION_ID);

        //THEN
        assertThat(filteredSignal.getDittoHeaders()).contains(Map.entry(requestedAcks, "[]"));
    }

}

