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
package org.eclipse.ditto.model.thingsearch;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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

    ImmutableSortOptionEntry(final SortOrder sortOrder, final CharSequence propertyPath) {
        this.propertyPath = JsonFactory.newPointer(checkNotNull(propertyPath, "property path"));
        this.sortOrder = sortOrder;
    }

    /**
     * Constructs new SortOptionEntry.
     *
     * @param sortOrder the sortOrder.
     * @param propertyPath the propertyPath.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static ImmutableSortOptionEntry of(final SortOrder sortOrder, final CharSequence propertyPath) {
        return new ImmutableSortOptionEntry(sortOrder, propertyPath);
    }

    /**
     * Sort for propertyPath ascending.
     *
     * @param propertyPath the propertyPath.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static ImmutableSortOptionEntry asc(final CharSequence propertyPath) {
        return new ImmutableSortOptionEntry(SortOrder.ASC, propertyPath);
    }

    /**
     * Sort for propertyPath descending.
     *
     * @param propertyPath the propertyPath.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static ImmutableSortOptionEntry desc(final CharSequence propertyPath) {
        return new ImmutableSortOptionEntry(SortOrder.DESC, propertyPath);
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
