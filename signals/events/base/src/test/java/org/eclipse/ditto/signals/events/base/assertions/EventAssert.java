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
package org.eclipse.ditto.signals.events.base.assertions;

import org.eclipse.ditto.signals.events.base.Event;

/**
 * An Assert for {@link Event}s.
 */
public class EventAssert extends AbstractEventAssert<EventAssert, Event> {

    /**
     * Constructs a new {@code EventAssert} object.
     *
     * @param actual the event to be checked.
     */
    public EventAssert(final Event actual) {
        super(actual, EventAssert.class);
    }

}
