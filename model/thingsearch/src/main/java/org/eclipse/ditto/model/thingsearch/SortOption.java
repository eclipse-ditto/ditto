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

import java.util.List;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * This interface represents an option for sorting search results.
 */
@Immutable
public interface SortOption extends Option, Iterable<SortOptionEntry> {

    /**
     * Returns an unmodifiable List containing the entries of this sort option.
     *
     * @return the entries of this sort option.
     */
    List<SortOptionEntry> getEntries();

    /**
     * Adds the provided {@code propertyPath} with the given {@code order} to this SortOption and appends them in the
     * newly created new SortOption instance which is returned.
     *
     * @param propertyPath the propertyPath which to sort.
     * @param order the order with which to sort.
     * @return the new SortOption instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    SortOption add(CharSequence propertyPath, SortOptionEntry.SortOrder order);

    /**
     * Adds the provided {@link SortOptionEntry} containing {@code propertyPath} and {@code order} and appends them to
     * the newly created new SortOption instance which is returned.
     *
     * @param entry the SortOptionEntry to append.
     * @return the new SortOption instance.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    SortOption add(SortOptionEntry entry);

    /**
     * Returns the number of entries of this sort option.
     *
     * @return the number of entries of this sort option.
     */
    int getSize();

    /**
     * Indicates whether this sort option has entries.
     *
     * @return {@code true} if this sort option does not contain any entries, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the entries of this sort option as its source.
     *
     * @return a sequential stream of the entries of sort option result.
     */
    Stream<SortOptionEntry> stream();

    /**
     * Returns the string representation of this sort option. If this sort option is empty, an empty string is returned.
     * Otherwise the string consists of the prefix {@code "sort("} which is followed by a comma-separated list of the
     * sort option entries and finally of the suffix {@code ")"}. An example string could look like
     * {@code "sort(+thingId,-attributes/manufacturer)"}.
     *
     * @return the string representation of this sort option.
     */
    @Override
    String toString();

}
