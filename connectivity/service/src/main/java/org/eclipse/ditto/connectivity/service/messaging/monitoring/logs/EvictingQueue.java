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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.util.Collection;
import java.util.Queue;

import javax.annotation.Nullable;

/**
 * A queue with a maximum size that will automatically evict elements from the head of the queue when full.
 * @param <E> type of elements in the queue.
 */
interface EvictingQueue<E> extends Queue<E> {

    /**
     * Will add the element {@code e} to the queue. If the queue would exceed the capacity after the insert, an
     * element will be removed from the head of the queue first.
     *
     * @param e the element to add to the queue.
     * @return {@code true} as described in {@link java.util.Queue#add(Object)}.
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null and
     *         this queue does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this queue
     */
    @Override
    boolean add(@Nullable E e);

    /**
     * Will add the element {@code e} to the queue. If the queue would exceed the capacity after the insert, an
     * element will be removed from the head of the queue first.
     *
     * @param e the element to add to the queue.
     * @return {@code true} as described in {@link java.util.Queue#offer(Object)}.
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null and
     *         this queue does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this queue
     */
    @Override
    boolean offer(@Nullable E e);

    /**
     * Will add all elements of {@code c} to the queue. If the queue would exceed the capacity after the insert,
     * enough elements will be removed from the head of the queue.
     * @param c collection containing elements to be added to this collection.
     * @return {@code true} as described in {@link java.util.Queue#addAll(java.util.Collection)} }.
     * @throws UnsupportedOperationException if the <tt>addAll</tt> operation
     *         is not supported by this collection
     * @throws ClassCastException if the class of an element of the specified
     *         collection prevents it from being added to this collection
     * @throws NullPointerException if the specified collection contains a
     *         null element and this collection does not permit null elements,
     *         or if the specified collection is null
     * @throws IllegalArgumentException if some property of an element of the
     *         specified collection prevents it from being added to this
     *         collection
     * @throws IllegalStateException if not all the elements can be added at
     *         this time due to insertion restrictions
     */
    @Override
    boolean addAll(@Nullable Collection<? extends E> c);

}
