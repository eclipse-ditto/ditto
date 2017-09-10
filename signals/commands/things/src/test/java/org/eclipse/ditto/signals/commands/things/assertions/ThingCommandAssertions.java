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
package org.eclipse.ditto.signals.commands.things.assertions;

import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.assertions.CommandResponseAssert;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

/**
 * Custom assertions for commands.
 */
public class ThingCommandAssertions extends DittoBaseAssertions {

    public static ThingErrorResponseAssert assertThat(final ThingErrorResponse thingErrorResponse) {
        return new ThingErrorResponseAssert(thingErrorResponse);
    }

    /**
     * Returns an assert for {@link ThingQueryCommand}s.
     *
     * @param thingQueryCommand the command to be checked.
     * @return the Assert.
     */
    public static ThingQueryCommandAssert assertThat(final ThingQueryCommand<?> thingQueryCommand) {
        return new ThingQueryCommandAssert(thingQueryCommand);
    }

    /**
     * Returns an assert for {@link ThingModifyCommand}s.
     *
     * @param thingModifyCommand the command to be checked.
     * @return the Assert.
     */
    public static ThingModifyCommandAssert assertThat(final ThingModifyCommand<?> thingModifyCommand) {
        return new ThingModifyCommandAssert(thingModifyCommand);
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

    /**
     * Returns an Assert for {@link ThingModifyCommandResponse}s.
     *
     * @param commandResponse the command response to be checked.
     * @return the Assert.
     */
    public static ThingModifyCommandResponseAssert assertThat(final ThingModifyCommandResponse<?> commandResponse) {
        return new ThingModifyCommandResponseAssert(commandResponse);
    }

    /**
     * Returns an Assert for {@link ThingQueryCommandResponse}s.
     *
     * @param commandResponse the command response to be checked.
     * @return the Assert.
     */
    public static ThingQueryCommandResponseAssert assertThat(final ThingQueryCommandResponse<?> commandResponse) {
        return new ThingQueryCommandResponseAssert(commandResponse);
    }

}
