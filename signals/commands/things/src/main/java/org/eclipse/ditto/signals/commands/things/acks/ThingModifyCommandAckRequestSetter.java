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
 * {@link AcknowledgementRequest} for {@link DittoAcknowledgementLabel#TWIN_PERSISTED}.
 * If so, the result is a new command with extended headers, else the same command is returned.
 * The headers are only extended if the command is an instance of {@link ThingModifyCommand} if
 * {@link DittoHeaders#isResponseRequired()} evaluates to {@code true} and if command headers do not yet contain
 * acknowledgement requests.
 *
 * @since 1.1.0
 */
@Immutable
public final class ThingModifyCommandAckRequestSetter implements UnaryOperator<ThingModifyCommand<?>> {

    private static final ThingModifyCommandAckRequestSetter INSTANCE = new ThingModifyCommandAckRequestSetter();

    private ThingModifyCommandAckRequestSetter() {
        super();
    }

    /**
     * Returns an instance of {@code ThingModifyCommandAckRequestSetter}.
     *
     * @return the instance.
     */
    public static ThingModifyCommandAckRequestSetter getInstance() {
        return INSTANCE;
    }

    /**
     * @deprecated as of 1.2.0: use {@link #apply(org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand)} instead.
     */
    @Deprecated
    public Command<?> apply(final Command<?> command) {
        Command<?> result = checkNotNull(command, "command");
        if (isThingModifyCommand(command) && isResponseRequired(command)) {
            result = requestDittoPersistedAckIfNoOtherAcksAreRequested((ThingModifyCommand<?>) command);
        }
        return result;
    }

    @Override
    public ThingModifyCommand<?> apply(final ThingModifyCommand<?> command) {
        ThingModifyCommand<?> result = checkNotNull(command, "command");
        if (isResponseRequired(command)) {
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

    private static ThingModifyCommand<?> requestDittoPersistedAckIfNoOtherAcksAreRequested(
            final ThingModifyCommand<?> command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<AcknowledgementRequest> acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
        final boolean requestedAcksHeaderPresent =
                dittoHeaders.containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey());
        if (acknowledgementRequests.isEmpty() && !requestedAcksHeaderPresent) {
            acknowledgementRequests.add(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED));
            return command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                    .acknowledgementRequests(acknowledgementRequests)
                    .build());
        }
        return command;
    }

}
