/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;

/**
 * Immutable implementation of {@link ImportedLabels}.
 */
@Immutable
final class ImmutableImportedLabels extends AbstractSet<String> implements ImportedLabels {

    private final Set<String> entryLabels;

    private ImmutableImportedLabels(final Set<String> entryLabels) {
        checkNotNull(entryLabels, "entry labels");
        this.entryLabels = Collections.unmodifiableSet(new HashSet<>(entryLabels));
    }

    /**
     * Returns a new empty set of ImportedLabels.
     *
     * @return a new empty set of ImportedLabels.
     */
    public static ImportedLabels none() {
        return new ImmutableImportedLabels(Collections.emptySet());
    }

    /**
     * Returns a new {@code ImportedLabels} object which is initialised with the given entry labels.
     *
     * @param entryLabel the mandatory entryLabel to initialise the result with.
     * @param furtherEntryLabels additional entryLabels to initialise the result with.
     * @return a new {@code ImportedLabels} object which is initialised with {@code entryLabel} and {@code
     * furtherEntryLabels}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImportedLabels of(final CharSequence entryLabel, final CharSequence... furtherEntryLabels) {
        checkNotNull(entryLabel, "entryLabel");
        checkNotNull(furtherEntryLabels, "further entryLabels");

        final HashSet<String> entryLabels = new HashSet<>(1 + furtherEntryLabels.length);
        entryLabels.add(entryLabel.toString());
        Collections.addAll(entryLabels,
                Arrays.stream(furtherEntryLabels).map(CharSequence::toString).toArray(String[]::new));

        return new ImmutableImportedLabels(entryLabels);
    }

    /**
     * Returns a new {@code ImportedLabels} object which is initialised with the given entryLabels.
     *
     * @param entryLabels the entryLabels to initialise the result with.
     * @return a new {@code ImportedLabels} object which is initialised with {@code entryLabels}.
     * @throws NullPointerException if {@code entryLabels} is {@code null}.
     */
    public static ImportedLabels of(final Collection<String> entryLabels) {
        checkNotNull(entryLabels, "entryLabels");

        final HashSet<String> entryLabelSet = new HashSet<>();

        if (!entryLabels.isEmpty()) {
            entryLabelSet.addAll(entryLabels);
        }

        return new ImmutableImportedLabels(entryLabelSet);
    }

    @Override
    public boolean contains(final CharSequence label, final CharSequence... furtherLabels) {
        checkNotNull(label, "label whose presence is to be checked");
        checkNotNull(furtherLabels, "further labels whose presence are to be checked");

        final HashSet<String> entryLabelSet = new HashSet<>();
        entryLabelSet.add(label.toString());
        entryLabelSet.addAll(Arrays.stream(furtherLabels).map(CharSequence::toString).collect(Collectors.toSet()));

        return entryLabels.containsAll(entryLabelSet);
    }

    @Override
    public boolean contains(final ImportedLabels label) {
        checkNotNull(label, "labels whose presence is to be checked");

        return this.entryLabels.containsAll(label);
    }

    @Override
    public int size() {
        return entryLabels.size();
    }

    @Override
    public JsonArray toJson() {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        entryLabels.forEach(jsonArrayBuilder::add);
        return jsonArrayBuilder.build();
    }

    // now all methods from Collection which should not be supported as we have an immutable data structure:

    @Nonnull
    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            private final Iterator<String> i = entryLabels.iterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public String next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void forEachRemaining(final Consumer<? super String> action) {
                // Use backing collection version
                i.forEachRemaining(action);
            }
        };
    }

    @Override
    public boolean add(final String e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(@Nonnull final Collection<? extends String> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@Nonnull final Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@Nonnull final Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(final Predicate<? super String> filter) {
        throw new UnsupportedOperationException();
    }

}
