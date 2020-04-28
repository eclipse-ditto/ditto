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
package org.eclipse.ditto.protocoladapter;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * An {@code Adapter} maps objects of type {@link T} to an {@link Adaptable} and vice versa.
 *
 * @param <T> the type mapped by this {@code Adapter}.
 * @since 1.1.0
 */
public interface Adapter<T extends Jsonifiable<?>> {

    /**
     * Maps the given {@code adaptable} to its corresponding {@code T}.
     *
     * @param adaptable the adaptable to map.
     * @return the mapped object.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     */
    T fromAdaptable(Adaptable adaptable);

    /**
     * Maps the given {@code t} to it's corresponding {@code Adaptable} using the {@link TopicPath.Channel#TWIN Twin}
     * channel.
     *
     * @param t the object to map.
     * @return the mapped adaptable.
     * @throws NullPointerException if {@code t} is {@code null}.
     */
    default Adaptable toAdaptable(final T t) {
        return toAdaptable(t, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code t} to its corresponding {@code Adaptable}.
     *
     * @param t the object to map.
     * @param channel the channel that was used to send the signal
     * @return the mapped adaptable.
     * @throws NullPointerException if {@code t} is {@code null}.
     * @throws IllegalArgumentException if {@code channel} is unknown.
     */
    Adaptable toAdaptable(T t, TopicPath.Channel channel);

    /**
     * Retrieve the set of groups supported by this adapter.
     *
     * @return the supported groups.
     */
    Set<TopicPath.Group> getGroups();

    /**
     * Retrieve the set of channels supported by this adapter.
     *
     * @return the supported channels.
     */
    Set<TopicPath.Channel> getChannels();

    /**
     * Retrieve the set of criteria supported by this adapter.
     *
     * @return the supported criteria.
     */
    Set<TopicPath.Criterion> getCriteria();

    /**
     * Retrieve the set of actions supported by this adapter.
     *
     * @return the set of actions.
     */
    Set<TopicPath.Action> getActions();

    /**
     * Return the set of search actions supported by this adapter.
     * It is the empty set by default.
     *
     * @return the collection of supported search actions.
     */
    default Set<TopicPath.SearchAction> getSearchActions() {
        return Collections.emptySet();
    }

    /**
     * Retrieve whether this adapter is for responses.
     *
     * @return whether this adapter is for responses.
     */
    boolean isForResponses();

    /**
     * Retrieve whether this adapter requires a subject in the topic.
     * Only relevant for message commands and responses and acknowledgements.
     *
     * @return whether a subject in the topic is required.
     */
    default boolean requiresSubject() {
        return false;
    }
}
