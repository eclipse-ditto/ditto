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
package org.eclipse.ditto.thingsearch.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link SortOption}.
 */
@Immutable
final class ImmutableSortOption implements SortOption {

    private final List<SortOptionEntry> entries;

    private ImmutableSortOption(final List<SortOptionEntry> theEntries) {
        entries = Collections.unmodifiableList(new ArrayList<>(theEntries));
    }

    /**
     * Returns a new instance of {@code ImmutableSortOption} with the given entries.
     *
     * @param entries the entries of the returned sort option.
     * @return the new immutable sort option.
     * @throws NullPointerException if {@code entries} is {@code null}.
     */
    public static ImmutableSortOption of(final List<SortOptionEntry> entries) {
        return new ImmutableSortOption(checkNotNull(entries, "entries"));
    }

    @Override
    public void accept(final OptionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public List<SortOptionEntry> getEntries() {
        return entries;
    }

    @Override
    public SortOption add(final SortOptionEntry entry) {
        checkNotNull(entry, "entry to be added");
        final List<SortOptionEntry> newEntries = new ArrayList<>(entries);
        newEntries.add(entry);
        return new ImmutableSortOption(newEntries);
    }

    @Override
    public SortOption add(final CharSequence propertyPath, final SortOptionEntry.SortOrder order) {
        return add(new ImmutableSortOptionEntry(propertyPath, order));
    }

    @Override
    public int getSize() {
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public Stream<SortOptionEntry> stream() {
        return entries.stream();
    }

    @Override
    public Iterator<SortOptionEntry> iterator() {
        return entries.iterator();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSortOption that = (ImmutableSortOption) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "";
        }

        final String delimiter = ",";
        final String prefix = "sort(";
        final String suffix = ")";

        return stream() //
                .map(SortOptionEntry::toString) //
                .collect(Collectors.joining(delimiter, prefix, suffix));
    }

}
