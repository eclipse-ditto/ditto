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
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link JsonFieldSelector}.
 */
@Immutable
final class ImmutableJsonFieldSelector implements JsonFieldSelector {

    static final String COMMA = ",";

    private final Set<JsonPointer> pointers;
    private final String jsonFieldSelectorString;

    private ImmutableJsonFieldSelector(final Iterable<JsonPointer> thePointers,
            @Nullable final String theJsonFieldSelectorString) {

        final Set<JsonPointer> pointersSet = new LinkedHashSet<>();
        thePointers.forEach(p -> {
            if (!p.isEmpty()) {
                pointersSet.add(p);
            }
        });
        pointers = Collections.unmodifiableSet(pointersSet);
        jsonFieldSelectorString = theJsonFieldSelectorString != null ? theJsonFieldSelectorString :
                calculateFieldSelectorString(pointers);
    }

    /**
     * Returns a new instance of {@code ImmutableJsonFieldSelector} with empty JSON pointers.
     *
     * @return a new empty JSON field selector.
     */
    public static ImmutableJsonFieldSelector empty() {
        return of(Collections.emptyList(), null);
    }

    /**
     * Returns a new instance of {@code ImmutableJsonFieldSelector} based on the given JSON pointers. The order of the
     * pointers is maintained, for example if {@code pointers} is sorted or a LinkedHashSet.
     *
     * @param pointers a set of {@link JsonPointer}s which are the base of the returned field selector. Empty pointers
     * are ignored.
     * @return a new JSON field selector which is based on the given JSON pointers.
     * @throws NullPointerException if {@code pointers} is {@code null}.
     */
    public static ImmutableJsonFieldSelector of(final Iterable<JsonPointer> pointers) {
        return of(pointers, null);
    }

    /**
     * Returns a new instance of {@code ImmutableJsonFieldSelector} based on the given JSON pointers. The order of the
     * pointers is maintained, for example if {@code pointers} is sorted or a LinkedHashSet.
     *
     * @param pointers the {@link JsonPointer}s which are the base of the returned field selector. Empty pointers are
     * ignored.
     * @param fieldSelectorString the original String representation of the JSON pointers.
     * @return a new JSON field selector which is based on the given JSON pointers.
     * @throws NullPointerException if {@code pointers} is {@code null}.
     */
    public static ImmutableJsonFieldSelector of(final Iterable<JsonPointer> pointers,
            @Nullable final String fieldSelectorString) {

        requireNonNull(pointers, "The JSON pointers must not be null!");

        return new ImmutableJsonFieldSelector(pointers, fieldSelectorString);
    }

    @Override
    public Set<JsonPointer> getPointers() {
        return pointers;
    }

    @Override
    public int getSize() {
        return pointers.size();
    }

    @Override
    public boolean isEmpty() {
        return pointers.isEmpty();
    }

    @Override
    public Iterator<JsonPointer> iterator() {
        return pointers.iterator();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonFieldSelector that = (ImmutableJsonFieldSelector) o;
        return Objects.equals(pointers, that.pointers)
                && Objects.equals(jsonFieldSelectorString, that.jsonFieldSelectorString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointers, jsonFieldSelectorString);
    }

    @Override
    public String toString() {
        return jsonFieldSelectorString;
    }

    private static String calculateFieldSelectorString(final Collection<JsonPointer> thePointers) {
        return thePointers.stream()
                .map(JsonPointer::toString)
                .collect(Collectors.joining(COMMA));
    }

}
