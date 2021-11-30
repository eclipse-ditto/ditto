/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * Default ThingEventObserver implementation.
 */
public class DefaultThingEventObserver extends ThingEventObserver {

    @Override
    public void processThingEvent(final ThingEvent<?> event) {
        // noop
    }
}
