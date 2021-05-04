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
package org.eclipse.ditto.base.model.signals.events.assertions;

import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * An Assert for {@link org.eclipse.ditto.base.model.signals.events.Event}s.
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
