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
