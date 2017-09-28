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

import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.assertions.EventAssert;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * Custom assertions for commands.
 */
public class ThingEventAssertions extends DittoBaseAssertions {

    /**
     * Returns an assert for an {@link Event}.
     *
     * @param event the event to be checked.
     * @return the Assert.
     */
    public static EventAssert assertThat(final Event<?> event) {
        return new EventAssert(event);
    }

    /**
     * Returns an assert for ThingEvents.
     *
     * @param thingEvent the event to be checked.
     * @return the Assert.
     */
    public static ThingEventAssert assertThat(final ThingEvent<?> thingEvent) {
        return new ThingEventAssert(thingEvent);
    }

    /**
     * Returns an assert for {@link ThingModifiedEvent}s.
     *
     * @param thingModifiedEvent the event to be checked.
     * @return the Assert.
     */
    public static ThingModifiedEventAssert assertThat(final ThingModifiedEvent<?> thingModifiedEvent) {
        return new ThingModifiedEventAssert(thingModifiedEvent);
    }

}
