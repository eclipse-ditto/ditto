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
package org.eclipse.ditto.signals.events.things.assertions;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.signals.events.base.assertions.AbstractEventAssert;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * An Assert for {@link ThingEvent}s.
 */
public final class ThingEventAssert extends AbstractEventAssert<ThingEventAssert, ThingEvent> {

    /**
     * Constructs a new {@code ThingEventAssert} object.
     *
     * @param actual the event to be checked.
     */
    public ThingEventAssert(final ThingEvent actual) {
        super(actual, ThingEventAssert.class);
    }

    public ThingEventAssert hasThingId(final CharSequence expectedThingId) {
        isNotNull();
        final String actualThingId = actual.getThingId();
        Assertions.assertThat(actualThingId)
                .overridingErrorMessage("Expected ThingEvent to have Thing ID\n<%s> but it had\n<%s>",
                        expectedThingId, actualThingId)
                .isEqualTo(expectedThingId.toString());
        return this;
    }

}
