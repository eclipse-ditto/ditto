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
package org.eclipse.ditto.things.model.signals.commands.assertions;

import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.assertions.CommandResponseAssert;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

/**
 * Custom assertions for commands.
 */
public class ThingCommandAssertions extends DittoBaseAssertions {

    public static ThingErrorResponseAssert assertThat(final ThingErrorResponse thingErrorResponse) {
        return new ThingErrorResponseAssert(thingErrorResponse);
    }

    /**
     * Returns an assert for {@link org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand}s.
     *
     * @param thingQueryCommand the command to be checked.
     * @return the Assert.
     */
    public static ThingQueryCommandAssert assertThat(final ThingQueryCommand<?> thingQueryCommand) {
        return new ThingQueryCommandAssert(thingQueryCommand);
    }

    /**
     * Returns an assert for {@link org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand}s.
     *
     * @param thingModifyCommand the command to be checked.
     * @return the Assert.
     */
    public static ThingModifyCommandAssert assertThat(final ThingModifyCommand<?> thingModifyCommand) {
        return new ThingModifyCommandAssert(thingModifyCommand);
    }

    /**
     * Returns an Assert for {@link org.eclipse.ditto.base.model.signals.commands.CommandResponse}s.
     *
     * @param commandResponse the command response to be checked.
     * @return the Assert.
     */
    public static CommandResponseAssert assertThat(final CommandResponse commandResponse) {
        return new CommandResponseAssert(commandResponse);
    }

    /**
     * Returns an Assert for {@link org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse}s.
     *
     * @param commandResponse the command response to be checked.
     * @return the Assert.
     */
    public static ThingModifyCommandResponseAssert assertThat(final ThingModifyCommandResponse<?> commandResponse) {
        return new ThingModifyCommandResponseAssert(commandResponse);
    }

    /**
     * Returns an Assert for {@link org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse}s.
     *
     * @param commandResponse the command response to be checked.
     * @return the Assert.
     */
    public static ThingQueryCommandResponseAssert assertThat(final ThingQueryCommandResponse<?> commandResponse) {
        return new ThingQueryCommandResponseAssert(commandResponse);
    }

}
