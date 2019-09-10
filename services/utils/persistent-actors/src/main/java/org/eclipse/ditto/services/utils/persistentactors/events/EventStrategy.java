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
package org.eclipse.ditto.services.utils.persistentactors.events;

import javax.annotation.Nullable;

/**
 * This interface represents a strategy for handling events in a persistent actor.
 *
 * @param <E> type of the event this strategy matches against.
 * @param <S> type of entity.
 */
@FunctionalInterface
public interface EventStrategy<E, S> {

    /**
     * Applies an event to an entity.
     *
     * @param event the event to apply.
     * @param entity the entity to apply the event to.
     * @param revision the next revision of the entity.
     * @return the Thing with the event applied or {@code null} if no strategy for {@code event} could be found.
     * @throws NullPointerException if {@code event} is {@code null}.
     */
    @Nullable
    S handle(E event, @Nullable S entity, long revision);

}
