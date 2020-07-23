package org.eclipse.ditto.services.concierge.enforcement;/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;
import org.junit.Test;

import akka.actor.ActorRef;

public final class ResponseReceiverTest {

    private ResponseReceiver responseReceiver;

    @Test
    public void getsCorrectActorRef() {
        ActorRef actorRef = mock(ActorRef.class);
        this.responseReceiver = ResponseReceiver.of(actorRef, DittoHeaders.empty());

        assertThat(responseReceiver.ref()).isSameAs(actorRef);
    }

    @Test
    public void headersAreRemovedFromIncomingSignalOnEnhance() {
        //Given
        ActorRef actorRef = mock(ActorRef.class);
        this.responseReceiver = ResponseReceiver.of(actorRef, DittoHeaders.empty());

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .inboundPayloadMapper("someValue")
                .expectedResponseTypes(ResponseType.ERROR)
                .replyTarget(4711)
                .build();
        final RetrievePolicyIdResponse response =
                RetrievePolicyIdResponse.of(ThingId.of("namespace", "thing"), PolicyId.of("namespace", "policy"),
                        dittoHeaders);

        //When
        final DittoHeaders enhancedDittoHeaders = responseReceiver.enhance(response).getDittoHeaders();

        //Then
        assertThat(enhancedDittoHeaders).isEmpty();
    }

    @Test
    public void headersAreReplacesInIncomingSignalOnEnhance() {
        //Given
        ActorRef actorRef = mock(ActorRef.class);
        final DittoHeaders replacingHeaders = DittoHeaders.newBuilder()
                .inboundPayloadMapper("otherValue")
                .expectedResponseTypes(ResponseType.RESPONSE)
                .replyTarget(4712)
                .build();
        this.responseReceiver = ResponseReceiver.of(actorRef, replacingHeaders);

        final DittoHeaders signalHeaders = DittoHeaders.newBuilder()
                .inboundPayloadMapper("someValue")
                .expectedResponseTypes(ResponseType.ERROR)
                .replyTarget(4711)
                .correlationId("someId")
                .build();
        final RetrievePolicyIdResponse response =
                RetrievePolicyIdResponse.of(ThingId.of("namespace", "thing"), PolicyId.of("namespace", "policy"),
                        signalHeaders);

        //When
        final DittoHeaders enhancedDittoHeaders = responseReceiver.enhance(response).getDittoHeaders();

        //Then
        assertThat(enhancedDittoHeaders.getReplyTarget())
                .isEqualTo(replacingHeaders.getReplyTarget());
        assertThat(enhancedDittoHeaders.getInboundPayloadMapper())
                .isEqualTo(replacingHeaders.getInboundPayloadMapper());
        assertThat(enhancedDittoHeaders.getExpectedResponseTypes())
                .isEqualTo(replacingHeaders.getExpectedResponseTypes());
        assertThat(enhancedDittoHeaders.getCorrelationId()).isEqualTo(signalHeaders.getCorrelationId());
    }

}
