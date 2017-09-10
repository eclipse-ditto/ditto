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

import org.eclipse.ditto.signals.commands.base.assertions.AbstractCommandAssert;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * An abstract Assert for {@link ThingCommand}s.
 */
public abstract class AbstractThingCommandAssert<S extends AbstractThingCommandAssert<S, C>, C extends ThingCommand>
        extends AbstractCommandAssert<S, C> {

    /**
     * Constructs a new {@code AbstractThingCommandAssert} object.
     *
     * @param actual the command to be checked.
     * @param selfType the type of the actual Assert.
     */
    protected AbstractThingCommandAssert(final C actual, final Class<? extends AbstractCommandAssert> selfType) {
        super(actual, selfType);
    }

    public S withId(final CharSequence expectedId) {
        return assertThatEquals(actual.getId(), null != expectedId ? String.valueOf(expectedId) : null, "id");
    }

    public S withThingId(final CharSequence expectedThingId) {
        return assertThatEquals(actual.getThingId(), null != expectedThingId ? String.valueOf(expectedThingId) : null,
                "thingId");
    }

}
