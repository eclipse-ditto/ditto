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

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * This UnaryOperator accepts a Command and checks whether its DittoHeaders should be extended by the required ACK label
 * {@link DittoAcknowledgementLabel#PERSISTED}.
 * If so, the result is a new command with extended headers, else the same command is returned.
 * The headers are only extended if the command is an instance of {@link ThingModifyCommand} and if
 * {@link DittoHeaders#isResponseRequired()} evaluates to {@code true}.
 * Already set required ACK labels in headers are extended but remain untouched apart form that.
 */
@Immutable
final class ThingModifyCommandAckLabelSetter implements UnaryOperator<Command<?>> {

    private ThingModifyCommandAckLabelSetter() {
        super();
    }

    /**
     * Returns an instance of {@code CommandAckLabelSetter}.
     *
     * @return the instance.
     */
    public static ThingModifyCommandAckLabelSetter getInstance() {
        return new ThingModifyCommandAckLabelSetter();
    }

    @Override
    public Command<?> apply(final Command<?> command) {
        Command<?> result = checkNotNull(command, "command");
        if (isThingModifyCommand(command) && isResponseRequired(command)) {
            result = addDittoPersistedAckLabelToHeaders(command);
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

    private static Command<?> addDittoPersistedAckLabelToHeaders(final Command<?> command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<AcknowledgementLabel> requestedAckLabels = dittoHeaders.getRequestedAckLabels();
        requestedAckLabels.add(DittoAcknowledgementLabel.PERSISTED);
        return command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                .requestedAckLabels(requestedAckLabels)
                .build());
    }

}
