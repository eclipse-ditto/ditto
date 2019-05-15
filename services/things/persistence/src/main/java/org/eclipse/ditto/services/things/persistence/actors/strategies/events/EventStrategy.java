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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This interface represents a strategy for handling events in the
 * {@link org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor}.
 *
 * @param <T> type of the event this strategy matches against.
 */
@FunctionalInterface
public interface EventStrategy<T extends ThingEvent> {

    /**
     * Applies an event to a Thing.
     *
     * @param event the event to apply.
     * @param thing the Thing to apply the event to.
     * @param revision the next revision of the Thing.
     * @return the Thing with the event applied or {@code null} if no strategy for {@code event} could be found.
     * @throws NullPointerException if {@code event} is {@code null}.
     */
    @Nullable
    Thing handle(T event, @Nullable Thing thing, long revision);

}
