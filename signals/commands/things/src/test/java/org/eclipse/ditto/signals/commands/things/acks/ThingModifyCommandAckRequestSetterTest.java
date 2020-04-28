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
package org.eclipse.ditto.signals.commands.things.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.Test;

/**
 * Unit test for {@link ThingModifyCommandAckRequestSetter}.
 */
public final class ThingModifyCommandAckRequestSetterTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingModifyCommandAckRequestSetter.class, areImmutable());
    }

    @Test
    public void tryToApplyNullCommand() {
        final ThingModifyCommandAckRequestSetter underTest = ThingModifyCommandAckRequestSetter.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The command must not be null!")
                .withNoCause();
    }

    @Test
    public void doNothingIfNoResponseRequired() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED))
                .randomCorrelationId()
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final ThingModifyCommandAckRequestSetter underTest = ThingModifyCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(command);
    }

    @Test
    public void addPersistedAckLabelByDefault() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final CreateThing expected = command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED))
                .build());
        final ThingModifyCommandAckRequestSetter underTest = ThingModifyCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(expected);
    }

    @Test
    public void doNotAddPersistedAckLabelToAlreadyRequiredAckLabels() {
        final AcknowledgementRequest ackRequest1 = AcknowledgementRequest.of(AcknowledgementLabel.of("FOO"));
        final AcknowledgementRequest ackRequest2 = AcknowledgementRequest.of(AcknowledgementLabel.of("BAR"));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .acknowledgementRequest(ackRequest1, ackRequest2)
                .randomCorrelationId()
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final ThingModifyCommandAckRequestSetter underTest = ThingModifyCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(command);
    }

}