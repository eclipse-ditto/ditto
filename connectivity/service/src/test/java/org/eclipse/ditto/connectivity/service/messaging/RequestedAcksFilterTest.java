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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.placeholders.PlaceholderFunctionSignatureInvalidException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.junit.Test;

/**
 * Tests {@link RequestedAcksFilter}.
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
                RequestedAcksFilter.filterAcknowledgements(signal, externalMessage, "fn:filter('2+2','ne','5')", CONNECTION_ID);
        assertThat(notFilteredSignal.getDittoHeaders()).doesNotContainKey(requestedAcks);

        final Signal<?> filteredSignal =
                RequestedAcksFilter.filterAcknowledgements(signal, externalMessage, "fn:filter('2+2','eq','5')", CONNECTION_ID);
        assertThat(filteredSignal.getDittoHeaders()).contains(Map.entry(requestedAcks, "[]"));

        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                RequestedAcksFilter.filterAcknowledgements(signal, externalMessage, "fn:filter('2','+','2','eq','5')", CONNECTION_ID)
        );

        final Signal<?> defaultValueSetSignal =
                RequestedAcksFilter.filterAcknowledgements(signal, externalMessage, "fn:default('[\"twin-persisted\"]')", CONNECTION_ID);
        assertThat(defaultValueSetSignal.getDittoHeaders().getAcknowledgementRequests())
                .containsExactly(twinPersistedAckRequest);

        final Signal<?> transformedSignal =
                RequestedAcksFilter.filterAcknowledgements(signalWithRequestedAcks, externalMessage,
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
                RequestedAcksFilter.filterAcknowledgements(signal, externalMessage, "fn:filter(header:qos,'ne','0')", CONNECTION_ID);

        //THEN
        assertThat(filteredSignal.getDittoHeaders()).contains(Map.entry(requestedAcks, "[]"));
    }

}

