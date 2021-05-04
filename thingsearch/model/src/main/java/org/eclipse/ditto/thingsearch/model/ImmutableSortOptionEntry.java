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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Immutable implementation of {@link SortOptionEntry}.
 */
@Immutable
final class ImmutableSortOptionEntry implements SortOptionEntry {

    private final JsonPointer propertyPath;
    private final SortOrder sortOrder;

    ImmutableSortOptionEntry(final CharSequence propertyPath, final SortOrder sortOrder) {
        this.propertyPath = JsonFactory.newPointer(checkNotNull(propertyPath, "property path"));
        this.sortOrder = sortOrder;
    }

    /**
     * Constructs new SortOptionEntry.
     *
     * @param propertyPath the propertyPath.
     * @param sortOrder the sortOrder.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static ImmutableSortOptionEntry of(final CharSequence propertyPath, final SortOrder sortOrder) {
        return new ImmutableSortOptionEntry(propertyPath, sortOrder);
    }

    /**
     * Sort for propertyPath ascending.
     *
     * @param propertyPath the propertyPath.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static ImmutableSortOptionEntry asc(final CharSequence propertyPath) {
        return new ImmutableSortOptionEntry(propertyPath, SortOrder.ASC);
    }

    /**
     * Sort for propertyPath descending.
     *
     * @param propertyPath the propertyPath.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static ImmutableSortOptionEntry desc(final CharSequence propertyPath) {
        return new ImmutableSortOptionEntry(propertyPath, SortOrder.DESC);
    }

    @Override
    public JsonPointer getPropertyPath() {
        return propertyPath;
    }

    @Override
    public SortOrder getOrder() {
        return sortOrder;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSortOptionEntry that = (ImmutableSortOptionEntry) o;
        return Objects.equals(propertyPath, that.propertyPath) && sortOrder == that.sortOrder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyPath, sortOrder);
    }

    @Override
    public String toString() {
        return sortOrder.getName() + propertyPath.toString();
    }

}
