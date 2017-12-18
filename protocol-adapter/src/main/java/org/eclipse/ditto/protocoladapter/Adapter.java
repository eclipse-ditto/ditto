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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * An {@code Adapter} maps objects of type {@link T} to an {@link Adaptable} and vice versa.
 *
 * @param <T> the type mapped by this {@code Adapter}.
 */
public interface Adapter<T extends Jsonifiable> {

    /**
     * Maps the given {@code adaptable} to it's corresponding {@code T}.
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
     * Maps the given {@code t} to it's corresponding {@code Adaptable}.
     *
     * @param t the object to map.
     * @param channel the Channel (Twin/Live) to use.
     * @return the mapped adaptable.
     * @throws NullPointerException if {@code t} is {@code null}.
     * @throws IllegalArgumentException if {@code channel} is unknown.
     */
    Adaptable toAdaptable(T t, TopicPath.Channel channel);

}
