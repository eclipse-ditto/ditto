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

import java.util.function.UnaryOperator;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * This UnaryOperator accepts a ThingCommand and checks whether its DittoHeaders should be extended by an
 * {@link org.eclipse.ditto.model.base.acks.AcknowledgementRequest} for {@link org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel#LIVE_RESPONSE}.
 * If so, the result is a new command with extended headers, else the same command is returned.
 * The headers are only extended if {@link org.eclipse.ditto.model.base.headers.DittoHeaders#isResponseRequired()}
 * evaluates to {@code true} and if command headers do not yet contain acknowledgement requests.
 *
 * @since 1.1.0
 */
@Immutable
public final class ThingLiveCommandAckRequestSetter implements UnaryOperator<ThingCommand<?>> {

    private ThingLiveCommandAckRequestSetter() {
        super();
    }

    /**
     * Returns an instance of {@code CommandAckLabelSetter}.
     *
     * @return the instance.
     */
    public static ThingLiveCommandAckRequestSetter getInstance() {
        return new ThingLiveCommandAckRequestSetter();
    }

    /**
     * @since 1.2.0
     */
    @Override
    public ThingCommand<?> apply(final ThingCommand<?> command) {
        //TODO: implement
        return command;
    }

}
