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
package org.eclipse.ditto.services.gateway.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.Test;

/**
 * Unit test for {@link ThingModifyCommandAckLabelSetter}.
 */
public final class ThingModifyCommandAckLabelSetterTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingModifyCommandAckLabelSetter.class, areImmutable());
    }

    @Test
    public void tryToApplyNullCommand() {
        final ThingModifyCommandAckLabelSetter underTest = ThingModifyCommandAckLabelSetter.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The command must not be null!")
                .withNoCause();
    }

    @Test
    public void doNothingIfNoThingModifyCommand() {
        final ThingId thingId = ThingId.generateRandom();
        final Message<?> message =
                Message.newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, thingId, "my-subject").build())
                        .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(true)
                .randomCorrelationId()
                .build();
        final SendThingMessage<?> command = SendThingMessage.of(thingId, message, dittoHeaders);
        final ThingModifyCommandAckLabelSetter underTest = ThingModifyCommandAckLabelSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(command);

    }

    @Test
    public void doNothingIfNoResponseRequired() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(false)
                .randomCorrelationId()
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final ThingModifyCommandAckLabelSetter underTest = ThingModifyCommandAckLabelSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(command);
    }

    @Test
    public void addPersistedAckLabelByDefault() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final CreateThing expected = command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                .requestedAckLabels(DittoAcknowledgementLabel.PERSISTED)
                .build());
        final ThingModifyCommandAckLabelSetter underTest = ThingModifyCommandAckLabelSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(expected);
    }

    @Test
    public void doNotAddPersistedAckLabelToAlreadyRequiredAckLabels() {
        final AcknowledgementLabel ackLabel1 = AcknowledgementLabel.of("FOO");
        final AcknowledgementLabel ackLabel2 = AcknowledgementLabel.of("BAR");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .requestedAckLabels(ackLabel1, ackLabel2)
                .randomCorrelationId()
                .build();
        final CreateThing command = CreateThing.of(Thing.newBuilder().build(), null, dittoHeaders);
        final ThingModifyCommandAckLabelSetter underTest = ThingModifyCommandAckLabelSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(command);
    }

}