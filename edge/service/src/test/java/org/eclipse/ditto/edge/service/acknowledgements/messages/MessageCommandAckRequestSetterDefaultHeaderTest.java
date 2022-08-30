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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.edge.service.acknowledgements.message.MessageCommandAckRequestSetter;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test default header setting by {@link MessageCommandAckRequestSetter}.
 */
@RunWith(Parameterized.class)
public final class MessageCommandAckRequestSetterDefaultHeaderTest {

    private static final String[] TIMEOUT = new String[]{null, "0s", "1s"};
    private static final String[] RESPONSE_REQUIRED = new String[]{null, "false", "true"};
    private static final String[] REQUESTED_ACKS = new String[]{null, "[]", "[\"live-response\"]"};

    @Parameterized.Parameters(name = "timeout={0} response-required={1} requested-acks={2}")
    public static List<String[]> parameters() {
        return Arrays.stream(TIMEOUT).flatMap(timeout ->
                Arrays.stream(RESPONSE_REQUIRED).flatMap(responseRequired ->
                        Arrays.stream(REQUESTED_ACKS).map(requestedAcks ->
                                new String[]{timeout, responseRequired, requestedAcks}
                        )

                )
        ).collect(Collectors.toList());
    }

    @Nullable private final String timeout;
    @Nullable private final String responseRequired;
    @Nullable private final String requestedAcks;

    public MessageCommandAckRequestSetterDefaultHeaderTest(@Nullable final String timeout,
            @Nullable final String responseRequired,
            @Nullable final String requestedAcks) {
        this.timeout = timeout;
        this.responseRequired = responseRequired;
        this.requestedAcks = requestedAcks;
    }

    @Test
    public void willNeverGenerateInvalidHeadersFromValidHeaders() {
        final MessageCommand<?, ?> input = getMessageCommand();
        final MessageCommand<?, ?> output = MessageCommandAckRequestSetter.getInstance().apply(input);
        final Validity ofInput = new Validity(input.getDittoHeaders());
        final Validity ofOutput = new Validity(output.getDittoHeaders());
        if (ofInput.isHeaderValid) {
            assertThat(ofOutput.isHeaderValid)
                    .describedAs(String.format("isTimeoutZero=%b isResponseRequired=%b areAcksNonempty=%b\noutput.dittoHeaders=%s",
                            ofOutput.isTimeoutZero, ofOutput.isResponseRequired, ofOutput.areAcksNonempty, output.getDittoHeaders()))
                    .isTrue();
        }
    }

    private MessageCommand<?, ?> getMessageCommand() {
        final Message<?> message = MessageCommandAckRequestSetterTest.MESSAGE;
        final DittoHeadersBuilder<?, ?> builder = DittoHeaders.newBuilder();
        if (timeout != null) {
            builder.putHeader(DittoHeaderDefinition.TIMEOUT.getKey(), timeout);
        }
        if (responseRequired != null) {
            builder.putHeader(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), responseRequired);
        }
        if (requestedAcks != null) {
            builder.putHeader(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), requestedAcks);
        }
        return SendClaimMessage.of(message.getEntityId(), message, builder.build());
    }

    private static final class Validity {

        private final boolean isTimeoutZero;
        private final boolean isResponseRequired;
        private final boolean areAcksNonempty;
        private final boolean isHeaderValid;

        private Validity(final DittoHeaders dittoHeaders) {
            isTimeoutZero = dittoHeaders.getTimeout().map(Duration::isZero).orElse(false);
            isResponseRequired = dittoHeaders.isResponseRequired();
            areAcksNonempty = !dittoHeaders.getAcknowledgementRequests().isEmpty();
            isHeaderValid = !(isTimeoutZero && (isResponseRequired || areAcksNonempty));
        }
    }
}
