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

import org.eclipse.ditto.signals.commands.base.Command;

/**
 * An Assert for {@link Command}s.
 */
public class CommandAssert extends AbstractCommandAssert<CommandAssert, Command> {

    /**
     * Constructs a new {@code AbstractThingCommandAssert} object.
     *
     * @param actual the command to be checked.
     */
    protected CommandAssert(final Command actual) {
        super(actual, CommandAssert.class);
    }

    public CommandAssert withId(final CharSequence expectedId) {
        return assertThatEquals(actual.getId(), null != expectedId ? String.valueOf(expectedId) : null, "id");
    }

}
