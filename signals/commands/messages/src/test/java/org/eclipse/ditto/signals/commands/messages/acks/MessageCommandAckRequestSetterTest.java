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
package org.eclipse.ditto.signals.commands.messages.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.junit.Test;

/**
 * Unit test for {@link MessageCommandAckRequestSetter}.
 */
public final class MessageCommandAckRequestSetterTest {

    private static final ThingId THING_ID = ThingId.of("test.ns", "theThingId");

    private static final String SUBJECT = "theSubject";
    private static final String CONTENT_TYPE = "application/xml";

    private static final String KNOWN_RAW_PAYLOAD_STR = "<some>42</some>";
    private static final byte[] KNOWN_RAW_PAYLOAD_BYTES = KNOWN_RAW_PAYLOAD_STR.getBytes(StandardCharsets.UTF_8);

    private static final Message<?> MESSAGE = MessagesModelFactory.newMessageBuilder(
            MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, SUBJECT)
                    .contentType(CONTENT_TYPE)
                    .build())
            .rawPayload(ByteBuffer.wrap(KNOWN_RAW_PAYLOAD_BYTES))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(MessageCommandAckRequestSetter.class, areImmutable());
    }

    @Test
    public void tryToApplyNullCommand() {
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The command must not be null!")
                .withNoCause();
    }

    @Test
    public void doNothingIfNoResponseRequired() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .randomCorrelationId()
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(command);
    }

    @Test
    public void addLiveResponseAckLabelByDefault() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .randomCorrelationId()
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final MessageCommand<?, ?> expected = command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build());
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(expected);
    }

    @Test
    public void filterOutOtherBuiltInDittoAcknowledgementLabels() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED),
                        AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .randomCorrelationId()
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();
        final MessageCommand<?, ?> expected = SendThingMessage.of(THING_ID, MESSAGE, expectedHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(expected);
    }

    @Test
    public void doNotAddLiveResponseAckLabelToAlreadyRequiredAckLabels() {
        final AcknowledgementRequest ackRequest1 = AcknowledgementRequest.of(AcknowledgementLabel.of("FOO"));
        final AcknowledgementRequest ackRequest2 = AcknowledgementRequest.of(AcknowledgementLabel.of("BAR"));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .acknowledgementRequest(ackRequest1, ackRequest2)
                .randomCorrelationId()
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        assertThat(underTest.apply(command)).isEqualTo(command);
    }

}