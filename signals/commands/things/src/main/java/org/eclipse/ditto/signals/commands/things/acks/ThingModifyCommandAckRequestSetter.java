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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.acks.AbstractCommandAckRequestSetter;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * This UnaryOperator accepts a ThingModifyCommand and checks whether its DittoHeaders should be extended by an
 * {@link AcknowledgementRequest} for {@link DittoAcknowledgementLabel#TWIN_PERSISTED}.
 * <p>
 * If so, the result is a new command with extended headers, else the same command is returned.
 * </p>
 *
 * @since 1.1.0
 */
@Immutable
public final class ThingModifyCommandAckRequestSetter extends AbstractCommandAckRequestSetter<ThingModifyCommand<?>> {

    private static final ThingModifyCommandAckRequestSetter INSTANCE = new ThingModifyCommandAckRequestSetter();

    private ThingModifyCommandAckRequestSetter() {
        super(DittoAcknowledgementLabel.TWIN_PERSISTED);
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
     * @param command the command that will be checked for adding an {@link AcknowledgementRequest}
     * for {@link DittoAcknowledgementLabel#TWIN_PERSISTED}.
     * @return the command with the correct headers.
     * @deprecated as of 1.2.0: use {@link AbstractCommandAckRequestSetter#apply} instead.
     */
    @Deprecated
    public Command<?> apply(final Command<?> command) {
        Command<?> result = checkNotNull(command, "command");
        if (command instanceof ThingModifyCommand) {
            result = apply((ThingModifyCommand<?>) command);
        }
        return result;
    }

    @Override
    public boolean isApplicable(final ThingModifyCommand<?> command) {
        checkNotNull(command, "command");
        return !isLiveChannelCommand(command);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<ThingModifyCommand<?>> getMatchedClass() {
        return (Class) ThingModifyCommand.class;
    }

    @Override
    protected boolean isBindResponseRequiredToRemovingImplicitLabel() {
        return false;
    }
}
