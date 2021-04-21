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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.acks.AbstractCommandAckRequestSetter;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;

/**
 * This UnaryOperator accepts a MessageCommand and checks whether its DittoHeaders should be extended by an
 * {@link AcknowledgementRequest} for {@link DittoAcknowledgementLabel#LIVE_RESPONSE}.
 * <p>
 * If so, the result is a new command with extended headers, else the same command is returned.
 * </p>
 *
 * @since 1.2.0
 */
@Immutable
public final class MessageCommandAckRequestSetter extends AbstractCommandAckRequestSetter<MessageCommand<?, ?>> {

    private static final MessageCommandAckRequestSetter INSTANCE = new MessageCommandAckRequestSetter();

    private MessageCommandAckRequestSetter() {
        super(DittoAcknowledgementLabel.LIVE_RESPONSE);
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
    public boolean isApplicable(final MessageCommand<?, ?> command) {
        checkNotNull(command, "command");
        return true;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public Class<MessageCommand<?, ?>> getMatchedClass() {
        return (Class) MessageCommand.class;
    }

    @Override
    protected boolean isBindResponseRequiredToAddingRemovingImplicitLabel() {
        return true;
    }
}
