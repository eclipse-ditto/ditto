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
