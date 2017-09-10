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

import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * An Assert for {@link ThingQueryCommand}s.
 */
public class ThingQueryCommandAssert extends AbstractThingQueryCommandAssert<ThingQueryCommandAssert,
        ThingQueryCommand> {

    /**
     * Constructs a new {@code ThingQueryCommandAssert} object.
     *
     * @param actual the command to be checked.
     */
    public ThingQueryCommandAssert(final ThingQueryCommand actual) {
        super(actual, ThingQueryCommandAssert.class);
    }

}
