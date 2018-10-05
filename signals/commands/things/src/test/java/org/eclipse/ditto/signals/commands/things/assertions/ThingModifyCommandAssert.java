/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.things.assertions;

import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * An Assert for {@link ThingModifyCommand}s.
 */
public class ThingModifyCommandAssert extends AbstractThingModifyCommandAssert<ThingModifyCommandAssert,
        ThingModifyCommand> {

    /**
     * Constructs a new {@code ThingModifyCommandAssert} object.
     *
     * @param actual the command to be checked.
     */
    public ThingModifyCommandAssert(final ThingModifyCommand actual) {
        super(actual, ThingModifyCommandAssert.class);
    }

}
