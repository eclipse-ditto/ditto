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
package org.eclipse.ditto.protocol.adapter;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * An {@code Adapter} maps objects of type {@link T} to an {@link org.eclipse.ditto.protocol.Adaptable} and vice versa.
 *
 * @param <T> the type mapped by this {@code Adapter}.
 * @since 1.1.0
 */
public interface EventAdapter<T extends Event<?>> extends Adapter<T> {

    @Override
    default Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.EVENTS);
    }

    @Override
    default Set<TopicPath.Action> getActions() {
        return EnumSet.of(TopicPath.Action.CREATED, TopicPath.Action.MODIFIED, TopicPath.Action.DELETED);
    }

    @Override
    default boolean isForResponses() {
        return false;
    }
}
