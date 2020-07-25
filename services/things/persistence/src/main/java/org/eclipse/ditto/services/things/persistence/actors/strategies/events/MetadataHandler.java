/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Handler that modifies or adds Metadata to a Thing based on a Event.
 * @param <T> Type of the Event
 */
interface MetadataHandler<T extends ThingEvent<T>> {

    /**
     * Gets Event, Thing and Builder to modify the Builder accordingly.
     * @param event Event to react upon
     * @param thing Thing instance before application of event
     * @param builder Builder of modified Thing
     * @return Builder with changes according to startegy
     */
    ThingBuilder.FromCopy handle(final T event, final Thing thing, final ThingBuilder.FromCopy builder);

}
