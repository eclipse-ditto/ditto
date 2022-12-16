/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
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
final class ImmutableImportedLabels extends AbstractSet<Label> implements ImportedLabels {

    private final Set<Label> entryLabels;

    private ImmutableImportedLabels(final Set<Label> entryLabels) {
        checkNotNull(entryLabels, "entryLabels");
        this.entryLabels = Collections.unmodifiableSet(new LinkedHashSet<>(entryLabels));
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
        checkNotNull(furtherEntryLabels, "furtherEntryLabels");

        final Set<Label> entryLabels = new LinkedHashSet<>(1 + furtherEntryLabels.length);
        entryLabels.add(Label.of(entryLabel));
        Collections.addAll(entryLabels,
                Arrays.stream(furtherEntryLabels).map(Label::of).toArray(Label[]::new));

        return new ImmutableImportedLabels(entryLabels);
    }

    /**
     * Returns a new {@code ImportedLabels} object which is initialised with the given entryLabels.
     *
     * @param entryLabels the entryLabels to initialise the result with.
     * @return a new {@code ImportedLabels} object which is initialised with {@code entryLabels}.
     * @throws NullPointerException if {@code entryLabels} is {@code null}.
     */
    public static ImportedLabels of(final Collection<Label> entryLabels) {
        checkNotNull(entryLabels, "entryLabels");

        final Set<Label> entryLabelSet = new LinkedHashSet<>(entryLabels);
        return new ImmutableImportedLabels(entryLabelSet);
    }

    @Override
    public boolean contains(final CharSequence label, final CharSequence... furtherLabels) {
        checkNotNull(label, "label");
        checkNotNull(furtherLabels, "furtherLabels");

        final Set<Label> entryLabelSet = new LinkedHashSet<>();
        entryLabelSet.add(Label.of(label));
        entryLabelSet.addAll(Arrays.stream(furtherLabels)
                .map(Label::of)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        return entryLabels.containsAll(entryLabelSet);
    }

    @Override
    public boolean contains(final ImportedLabels importedLabels) {
        checkNotNull(importedLabels, "importedLabels");

        return this.entryLabels.containsAll(importedLabels);
    }

    @Override
    public int size() {
        return entryLabels.size();
    }

    @Override
    public JsonArray toJson() {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        entryLabels.stream().map(Label::toString).forEach(jsonArrayBuilder::add);
        return jsonArrayBuilder.build();
    }

    // now all methods from Collection which should not be supported as we have an immutable data structure:

    @Nonnull
    @Override
    public Iterator<Label> iterator() {
        return new Iterator<Label>() {
            private final Iterator<Label> i = entryLabels.iterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Label next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void forEachRemaining(final Consumer<? super Label> action) {
                // Use backing collection version
                i.forEachRemaining(action);
            }
        };
    }

    @Override
    public boolean add(final Label e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(@Nonnull final Collection<? extends Label> coll) {
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
    public boolean removeIf(final Predicate<? super Label> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ImmutableImportedLabels strings = (ImmutableImportedLabels) o;
        return Objects.equals(entryLabels, strings.entryLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entryLabels);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entryLabels=" + entryLabels +
                "]";
    }
}
