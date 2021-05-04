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
package org.eclipse.ditto.things.model.signals.events.assertions;

import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.assertions.EventAssert;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;

/**
 * Custom assertions for commands.
 */
public class ThingEventAssertions extends DittoBaseAssertions {

    /**
     * Returns an assert for an {@link org.eclipse.ditto.base.model.signals.events.Event}.
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
     * Returns an assert for {@link org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent}s.
     *
     * @param thingModifiedEvent the event to be checked.
     * @return the Assert.
     */
    public static ThingModifiedEventAssert assertThat(final ThingModifiedEvent<?> thingModifiedEvent) {
        return new ThingModifiedEventAssert(thingModifiedEvent);
    }

}
