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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

import javax.annotation.Nonnull;

/**
 * Synchronized implementation of {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.EvictingQueue}.
 * @param <E> type of elements in the queue.
 */
// TODO: docs & test
final class SynchronizedEvictingQueue<E> implements EvictingQueue<E> {

    private final int capacity;
    private final Queue<E> elements;
    private final Object mutex;

    private SynchronizedEvictingQueue(final int capacity) {
        this.capacity = capacity;
        this.elements = new ArrayDeque<>(capacity);
        this.mutex = this;
    }

    public static <E> SynchronizedEvictingQueue<E> withCapacity(final int capacity) {
        return new SynchronizedEvictingQueue<>(capacity);
    }

    @Override
    public boolean add(final E e) {
        return offer(e);
    }

    @Override
    public boolean offer(final E e) {
        synchronized (mutex) {
            if (capacity == elements.size()) {
                elements.poll();
            }
            return elements.offer(e);
        }
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        synchronized (mutex) {
            return c.stream()
                .map(this::add)
                .reduce(false, (a1, a2) -> a1 || a2); // return true as long as at least one element was changed
        }
    }

    @Override
    public int size() {
        synchronized (mutex) {
            return elements.size();
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (mutex) {
            return elements.isEmpty();
        }
    }

    @Override
    public boolean contains(final Object o) {
        synchronized (mutex) {
            return elements.contains(o);
        }
    }

    @Override
    public Iterator<E> iterator() {
        synchronized (mutex) {
            return elements.iterator();
        }
    }

    @Override
    public Object[] toArray() {
        synchronized (mutex) {
            return elements.toArray();
        }
    }

    @Override
    public <T> T[] toArray(@Nonnull final T[] a) {
        synchronized (mutex) {
            return elements.toArray(a);
        }
    }

    @Override
    public boolean remove(final Object o) {
        synchronized (mutex) {
            return elements.remove(o);
        }
    }

    @Override
    public boolean containsAll(@Nonnull final Collection<?> c) {
        synchronized (mutex) {
            return elements.containsAll(c);
        }
    }


    @Override
    public boolean removeAll(@Nonnull final Collection<?> c) {
        synchronized (mutex) {
            return elements.removeAll(c);
        }
    }

    @Override
    public boolean retainAll(@Nonnull final Collection<?> c) {
        synchronized (mutex) {
            return elements.retainAll(c);
        }
    }

    @Override
    public void clear() {
        synchronized (mutex) {
          elements.clear();
        }
    }

    @Override
    public E remove() {
        synchronized (mutex) {
            return elements.remove();
        }
    }

    @Override
    public E poll() {
        synchronized (mutex) {
            return elements.poll();
        }
    }

    @Override
    public E element() {
        synchronized (mutex) {
            return elements.element();
        }
    }

    @Override
    public E peek() {
        synchronized (mutex) {
            return elements.peek();
        }
    }

}
