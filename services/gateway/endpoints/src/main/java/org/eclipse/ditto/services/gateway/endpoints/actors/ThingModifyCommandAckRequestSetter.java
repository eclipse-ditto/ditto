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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Set;
import java.util.function.UnaryOperator;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * This UnaryOperator accepts a Command and checks whether its DittoHeaders should be extended by an
 * {@link AcknowledgementRequest} for {@link DittoAcknowledgementLabel#PERSISTED}.
 * If so, the result is a new command with extended headers, else the same command is returned.
 * The headers are only extended if the command is an instance of {@link ThingModifyCommand} if
 * {@link DittoHeaders#isResponseRequired()} evaluates to {@code true} and if command headers do not yet contain
 * acknowledgement requests.
 */
@Immutable
final class ThingModifyCommandAckRequestSetter implements UnaryOperator<Command<?>> {

    private ThingModifyCommandAckRequestSetter() {
        super();
    }

    /**
     * Returns an instance of {@code CommandAckLabelSetter}.
     *
     * @return the instance.
     */
    public static ThingModifyCommandAckRequestSetter getInstance() {
        return new ThingModifyCommandAckRequestSetter();
    }

    @Override
    public Command<?> apply(final Command<?> command) {
        Command<?> result = checkNotNull(command, "command");
        if (isThingModifyCommand(command) && isResponseRequired(command)) {
            result = requestDittoPersistedAckIfNoOtherAcksAreRequested(command);
        }
        return result;
    }

    private static boolean isThingModifyCommand(final Command<?> command) {
        return ThingModifyCommand.class.isAssignableFrom(command.getClass());
    }

    private static boolean isResponseRequired(final WithDittoHeaders<?> command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    private static Command<?> requestDittoPersistedAckIfNoOtherAcksAreRequested(final Command<?> command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<AcknowledgementRequest> acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
        final boolean requestedAcksHeaderPresent =
                dittoHeaders.containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey());
        if (acknowledgementRequests.isEmpty() && !requestedAcksHeaderPresent) {
            acknowledgementRequests.add(AcknowledgementRequest.of(DittoAcknowledgementLabel.PERSISTED));
            return command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                    .acknowledgementRequests(acknowledgementRequests)
                    .build());
        }
        return command;
    }

}
