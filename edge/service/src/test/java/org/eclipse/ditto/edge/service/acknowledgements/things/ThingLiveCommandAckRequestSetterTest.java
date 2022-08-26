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
package org.eclipse.ditto.edge.service.acknowledgements.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.junit.Test;

/**
 * Unit test for {@link ThingLiveCommandAckRequestSetter}.
 */
public final class ThingLiveCommandAckRequestSetterTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingLiveCommandAckRequestSetter.class, areImmutable());
    }

    @Test
    public void tryToApplyNullCommand() {
        final ThingLiveCommandAckRequestSetter underTest = ThingLiveCommandAckRequestSetter.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The command must not be null!")
                .withNoCause();
    }

    @Test
    public void removeLiveResponseAckLabelIfNoResponseRequired() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .randomCorrelationId()
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final ThingLiveCommandAckRequestSetter underTest = ThingLiveCommandAckRequestSetter.getInstance();

        final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                .acknowledgementRequests(Collections.emptyList())
                .build();
        final CreateThing expectedCommand = command.setDittoHeaders(expectedHeaders);

        assertThat(underTest.apply(command)).isEqualTo(expectedCommand);
    }

    @Test
    public void addLiveResponseAckLabelByDefault() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .randomCorrelationId()
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final CreateThing expected = command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .responseRequired(true)
                .build());
        final ThingLiveCommandAckRequestSetter underTest = ThingLiveCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(expected);
    }

    @Test
    public void filterOutOtherBuiltInDittoAcknowledgementLabels() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED),
                        AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE),
                        AcknowledgementRequest.of(DittoAcknowledgementLabel.SEARCH_PERSISTED))
                .randomCorrelationId()
                .build();

        final Thing newThing = Thing.newBuilder().setGeneratedId().build();
        final CreateThing command = CreateThing.of(newThing, null, dittoHeaders);
        final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .responseRequired(true)
                .build();
        final CreateThing expected = CreateThing.of(newThing, null, expectedHeaders);
        final ThingLiveCommandAckRequestSetter underTest = ThingLiveCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(expected);
    }

    @Test
    public void addsLiveResponseAckLabelToAlreadyRequiredAckLabels() {
        final AcknowledgementRequest ackRequest1 = AcknowledgementRequest.of(AcknowledgementLabel.of("FOO"));
        final AcknowledgementRequest ackRequest2 = AcknowledgementRequest.of(AcknowledgementLabel.of("BAR"));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .acknowledgementRequest(ackRequest1, ackRequest2)
                .randomCorrelationId()
                .responseRequired(true)
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final ThingLiveCommandAckRequestSetter underTest = ThingLiveCommandAckRequestSetter.getInstance();

        final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                .acknowledgementRequest(ackRequest1, ackRequest2,
                        AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();
        final CreateThing expectedCommand = command.setDittoHeaders(expectedHeaders);

        assertThat(underTest.apply(command)).isEqualTo(expectedCommand);
    }

    @Test
    public void notAddingLiveResponseAckLabelToExplicitlyEmptyRequiredAckLabels() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .acknowledgementRequests(Collections.emptyList())
                .randomCorrelationId()
                .responseRequired(true)
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final ThingLiveCommandAckRequestSetter underTest = ThingLiveCommandAckRequestSetter.getInstance();

        final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                .acknowledgementRequests(Collections.emptyList())
                .build();
        final CreateThing expectedCommand = command.setDittoHeaders(expectedHeaders);

        assertThat(underTest.apply(command)).isEqualTo(expectedCommand);
    }

}
