/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superclass of event strategy repositories.
 *
 * @param <E> the type of the event
 * @param <S> the type of the entity
 */
@Immutable
public abstract class AbstractEventStrategies<E extends EventsourcedEvent<?>, S> implements EventStrategy<E, S> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<Class<? extends E>, EventStrategy<? extends E, S>> strategies = new HashMap<>();

    /**
     * Add an event strategy. Call in constructor only.
     *
     * @param cls class of events. Must be final.
     * @param strategy the strategy to handle the events.
     * @param <T> type of events.
     */
    protected <T extends E> void addStrategy(final Class<T> cls, final EventStrategy<T, S> strategy) {
        strategies.put(cls, strategy);
    }

    @Override
    @SuppressWarnings("unchecked")
    public S handle(final E event, @Nullable final S entity, final long revision) {
        checkNotNull(event, "event");
        final EventStrategy<E, S> strategy = (EventStrategy<E, S>) strategies.get(event.getClass());
        if (null != strategy) {
            return strategy.handle(event, entity, revision);
        } else {
            log.error("Ignoring event because no strategy is found: <{}>", event);
            return entity;
        }
    }

}
