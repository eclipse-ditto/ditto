/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.base.assertions;

import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;

/**
 * Custom assertions for commands.
 */
public class CommandAssertions extends DittoBaseAssertions {

    public static ErrorResponseAssert assertThat(final ErrorResponse errorResponse) {
        return new ErrorResponseAssert(errorResponse);
    }

    /**
     * Returns an Assert for {@link CommandResponse}s.
     *
     * @param command the command response to be checked.
     * @return the Assert.
     */
    public static CommandAssert assertThat(final Command command) {
        return new CommandAssert(command);
    }

    /**
     * Returns an Assert for {@link CommandResponse}s.
     *
     * @param commandResponse the command response to be checked.
     * @return the Assert.
     */
    public static CommandResponseAssert assertThat(final CommandResponse commandResponse) {
        return new CommandResponseAssert(commandResponse);
    }

}
