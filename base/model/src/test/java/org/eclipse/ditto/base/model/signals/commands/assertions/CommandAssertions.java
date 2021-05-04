/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands.assertions;

import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;

/**
 * Custom assertions for commands.
 */
public class CommandAssertions extends DittoBaseAssertions {

    public static ErrorResponseAssert assertThat(final ErrorResponse<?> errorResponse) {
        return new ErrorResponseAssert(errorResponse);
    }

    /**
     * Returns an Assert for {@link org.eclipse.ditto.base.model.signals.commands.CommandResponse}s.
     *
     * @param command the command response to be checked.
     * @return the Assert.
     */
    public static CommandAssert assertThat(final Command<?> command) {
        return new CommandAssert(command);
    }

    /**
     * Returns an Assert for {@link org.eclipse.ditto.base.model.signals.commands.CommandResponse}s.
     *
     * @param commandResponse the command response to be checked.
     * @return the Assert.
     */
    public static CommandResponseAssert assertThat(final CommandResponse<?> commandResponse) {
        return new CommandResponseAssert(commandResponse);
    }

}
