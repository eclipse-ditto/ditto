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

import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.signals.commands.assertions.AbstractCommandAssert;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;

/**
 * An abstract Assert for {@link org.eclipse.ditto.things.model.signals.commands.ThingCommand}s.
 */
public abstract class AbstractThingCommandAssert<S extends AbstractThingCommandAssert<S, C>, C extends ThingCommand<?>>
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
        return assertThatEquals(actual.getEntityId(), expectedId == null ? null : ThingId.of(expectedId),
                "id");
    }

    public S withId(final ThingId expectedThingId) {
        return assertThatEquals(actual.getEntityId(), expectedThingId, "thingId");
    }

}
