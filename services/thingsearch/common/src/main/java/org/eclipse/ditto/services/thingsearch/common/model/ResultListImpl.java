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
package org.eclipse.ditto.services.thingsearch.common.model;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Immutable implementation of {@link ResultList}.
 *
 * @param <E> the type of the items
 */
public final class ResultListImpl<E> implements ResultList<E> {

    private final List<E> items;
    private final long nextPageOffset;

    /**
     * Constructor.
     *
     * @param items the items
     * @param nextPageOffset the offset of the next page or {@link ResultList#NO_NEXT_PAGE}
     */
    public ResultListImpl(final List<E> items, final long nextPageOffset) {
        this.items = Collections.unmodifiableList(new ArrayList<>(requireNonNull(items)));
        this.nextPageOffset = nextPageOffset;
    }

    @Override
    public E get(final int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return items.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return items.iterator();
    }

    @Override
    public Object[] toArray() {
        return items.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return items.toArray(a);
    }

    @Override
    public boolean add(final E e) {
        return items.add(e);
    }

    @Override
    public boolean remove(final Object o) {
        return items.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return items.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        return items.addAll(c);
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends E> c) {
        return items.addAll(c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return items.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return items.retainAll(c);
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public E set(final int index, final E element) {
        return items.set(index, element);
    }

    @Override
    public void add(final int index, final E element) {
        items.add(index, element);
    }

    @Override
    public E remove(final int index) {
        return items.remove(index);
    }

    @Override
    public int indexOf(final Object o) {
        return items.indexOf(o);
    }

    @Override
    public int lastIndexOf(final Object o) {
        return items.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return items.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(final int index) {
        return items.listIterator(index);
    }

    @Override
    public List<E> subList(final int fromIndex, final int toIndex) {
        return items.subList(fromIndex, toIndex);
    }

    @Override
    public long nextPageOffset() {
        return nextPageOffset;
    }

    // CS:OFF
    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((items == null) ? 0 : items.hashCode());
        result = (prime * result) + (int) (nextPageOffset ^ (nextPageOffset >>> 32));
        return result;
    } // CS:ON

    // CS:OFF
    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object obj)
    // CS:ON
    {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        @SuppressWarnings("rawtypes")
        final ResultListImpl other = (ResultListImpl) obj;
        if (items == null) {
            if (other.items != null) {
                return false;
            }
        } else if (!items.equals(other.items)) {
            return false;
        }
        if (nextPageOffset != other.nextPageOffset) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ResultListImpl [items=" + items + ", nextPageOffset=" + nextPageOffset + "]";
    }

}
