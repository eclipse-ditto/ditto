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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Set;
import java.util.function.UnaryOperator;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;

/**
 * This UnaryOperator accepts a Command and checks whether its DittoHeaders should be extended by an
 * {@link AcknowledgementRequest} for {@link DittoAcknowledgementLabel#LIVE_RESPONSE}.
 * If so, the result is a new command with extended headers, else the same command is returned.
 * The headers are only extended if {@link DittoHeaders#isResponseRequired()}
 * evaluates to {@code true} and if command headers do not yet contain acknowledgement requests.
 *
 * @since 1.2.0
 */
@Immutable
public final class MessageCommandAckRequestSetter implements UnaryOperator<MessageCommand<?, ?>> {

    private static final MessageCommandAckRequestSetter INSTANCE = new MessageCommandAckRequestSetter();

    private MessageCommandAckRequestSetter() {
        super();
    }

    /**
     * Returns an instance of {@code MessageCommandAckRequestSetter}.
     *
     * @return the instance.
     */
    public static MessageCommandAckRequestSetter getInstance() {
        return INSTANCE;
    }

    @Override
    public MessageCommand<?, ?> apply(final MessageCommand<?, ?> command) {
        MessageCommand<?, ?> result = checkNotNull(command, "command");
        if (isResponseRequired(command)) {
            result = requestDittoLiveResponseAckIfNoOtherAcksAreRequested(command);
        }
        return result;
    }

    private static boolean isResponseRequired(final WithDittoHeaders<?> command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    private static MessageCommand<?, ?> requestDittoLiveResponseAckIfNoOtherAcksAreRequested(
            final MessageCommand<?, ?> command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<AcknowledgementRequest> acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
        final boolean requestedAcksHeaderPresent =
                dittoHeaders.containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey());
        if (acknowledgementRequests.isEmpty() && !requestedAcksHeaderPresent) {
            acknowledgementRequests.add(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE));
            return command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                    .acknowledgementRequests(acknowledgementRequests)
                    .build());
        }
        return command;
    }

}
