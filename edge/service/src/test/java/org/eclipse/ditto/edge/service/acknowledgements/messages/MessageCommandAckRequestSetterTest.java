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
package org.eclipse.ditto.edge.service.acknowledgements.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.edge.service.acknowledgements.message.MessageCommandAckRequestSetter;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.MessagesModelFactory;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.things.model.ThingId;
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

    static final Message<?> MESSAGE = MessagesModelFactory.newMessageBuilder(
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
    public void addLiveResponseAckLabelByDefault() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .randomCorrelationId()
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final MessageCommand<?, ?> expected = command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .responseRequired(true)
                .build());
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        Assertions.assertThat(underTest.apply(command)).isEqualTo(expected);
    }

    @Test
    public void removeLiveResponseAckLabelWhenResponseRequiredFalse() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .randomCorrelationId()
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final DittoHeaders expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequests(Collections.emptyList())
                .responseRequired(false)
                .build();
        final MessageCommand<?, ?> expected = command.setDittoHeaders(expectedHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        final MessageCommand<?, ?> appliedCommand = underTest.apply(command);
        assertThat(appliedCommand.getDittoHeaders()).isEqualTo(expectedHeaders);
        assertThat(appliedCommand).isEqualTo(expected);
    }

    @Test
    public void removeLiveResponseAckLabelFromOthersWhenResponseRequiredFalse() {
        final AcknowledgementRequest ackRequest1 = AcknowledgementRequest.of(AcknowledgementLabel.of("FOO"));
        final AcknowledgementRequest ackRequest2 = AcknowledgementRequest.of(AcknowledgementLabel.of("BAR"));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .responseRequired(false)
                .acknowledgementRequest(ackRequest1, AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE),
                        ackRequest2)
                .randomCorrelationId()
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final DittoHeaders expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequest(ackRequest1, ackRequest2)
                .responseRequired(false)
                .build();
        final MessageCommand<?, ?> expected = command.setDittoHeaders(expectedHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        final MessageCommand<?, ?> appliedCommand = underTest.apply(command);
        assertThat(appliedCommand.getDittoHeaders()).isEqualTo(expectedHeaders);
        assertThat(appliedCommand).isEqualTo(expected);
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
                .responseRequired(true)
                .build();
        final MessageCommand<?, ?> expected = SendThingMessage.of(THING_ID, MESSAGE, expectedHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        Assertions.assertThat(underTest.apply(command)).isEqualTo(expected);
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
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                .acknowledgementRequest(ackRequest1, ackRequest2,
                        AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();
        final MessageCommand<?, ?> expectedCommand = command.setDittoHeaders(expectedHeaders);

        final MessageCommand<?, ?> appliedCommand = underTest.apply(command);
        assertThat(appliedCommand.getDittoHeaders()).isEqualTo(expectedHeaders);
        assertThat(appliedCommand).isEqualTo(expectedCommand);
    }

    @Test
    public void notAddingLiveResponseAckLabelToExplicitlyEmptyRequiredAckLabels() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .channel("live")
                .acknowledgementRequests(Collections.emptyList())
                .randomCorrelationId()
                .responseRequired(true)
                .build();
        final MessageCommand<?, ?> command = SendThingMessage.of(THING_ID, MESSAGE, dittoHeaders);
        final MessageCommandAckRequestSetter underTest = MessageCommandAckRequestSetter.getInstance();

        final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                .acknowledgementRequests(Collections.emptyList())
                .build();
        final MessageCommand<?, ?> expectedCommand = command.setDittoHeaders(expectedHeaders);

        final MessageCommand<?, ?> appliedCommand = underTest.apply(command);
        assertThat(appliedCommand.getDittoHeaders()).isEqualTo(expectedHeaders);
        assertThat(appliedCommand).isEqualTo(expectedCommand);
    }

}
