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

package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Default implementation of {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.EvictingQueue}.
 * This implementation does not care about synchronization, as we don't care if there might be an element too much or
 * too little in the queue. We care about speed of the queue.
 *
 * @param <E> type of elements in the queue.
 */
@NotThreadSafe
final class DefaultEvictingQueue<E> extends AbstractQueue<E> implements EvictingQueue<E> {

    private final int capacity;
    private final Queue<E> elements;

    private DefaultEvictingQueue(final int capacity) {
        this.capacity = capacity;
        this.elements = new ConcurrentLinkedQueue<>();
    }

    /**
     * Create a new synchronized evicting queue.
     *
     * @param capacity capacity of the queue.
     * @param <E> type of elements in the queue.
     * @return a new instance of {@code DefaultEvictingQueue}.
     */
    static <E> DefaultEvictingQueue<E> withCapacity(final int capacity) {
        return new DefaultEvictingQueue<>(capacity);
    }

    @Override
    public Iterator<E> iterator() {
        return elements.iterator();
    }

    @Override
    public boolean offer(@Nullable final E e) {
        if (capacity == elements.size()) {
            elements.poll();
        }
        return elements.offer(e);
    }

    @Override
    public E poll() {
        return elements.poll();
    }

    @Override
    public E peek() {
        return elements.peek();
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultEvictingQueue<?> that = (DefaultEvictingQueue<?>) o;
        return capacity == that.capacity &&
                Objects.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, elements);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", capacity=" + capacity +
                ", elements=" + elements +
                "]";
    }

}
