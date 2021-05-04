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
package org.eclipse.ditto.thingsearch.service.common.model;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;

/**
 * Immutable implementation of {@link ResultList}.
 *
 * @param <E> the type of the items
 */
public final class ResultListImpl<E> implements ResultList<E> {

    private final List<E> items;
    private final long nextPageOffset;
    @Nullable private final JsonArray lastResultSortValues;

    /**
     * Constructor.
     *
     * @param items the items
     * @param nextPageOffset the offset of the next page or {@link ResultList#NO_NEXT_PAGE}
     */
    public ResultListImpl(final List<E> items, final long nextPageOffset) {
        this(items, nextPageOffset, null);
    }

    /**
     * Constructor.
     *
     * @param items the items
     * @param nextPageOffset the offset of the next page or {@link ResultList#NO_NEXT_PAGE}
     * @param lastResultSortValues sort values of the last result.
     */
    public ResultListImpl(final List<E> items, final long nextPageOffset,
            @Nullable final JsonArray lastResultSortValues) {
        this.items = Collections.unmodifiableList(new ArrayList<>(requireNonNull(items)));
        this.nextPageOffset = nextPageOffset;
        this.lastResultSortValues = lastResultSortValues;
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

    @Override
    public Optional<JsonArray> lastResultSortValues() {
        return Optional.ofNullable(lastResultSortValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, nextPageOffset, lastResultSortValues);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ResultListImpl)) {
            return false;
        } else {
            final ResultListImpl<?> that = (ResultListImpl<?>) obj;
            return Objects.equals(items, that.items) &&
                    Objects.equals(nextPageOffset, that.nextPageOffset) &&
                    Objects.equals(lastResultSortValues, that.lastResultSortValues);
        }
    }

    @Override
    public String toString() {
        return "ResultListImpl [items=" + items +
                ", nextPageOffset=" + nextPageOffset +
                ", lastResultSortValues=" + lastResultSortValues +
                "]";
    }

}
