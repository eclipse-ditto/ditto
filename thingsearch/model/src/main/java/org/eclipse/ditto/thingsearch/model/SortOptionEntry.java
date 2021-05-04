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

import org.eclipse.ditto.json.JsonPointer;

/**
 * A particular entry of the sort option. For example {@code +thingId} is an entry which conveys the meaning that the
 * Thing ID should be sorted in ascending order.
 */
public interface SortOptionEntry {

    /**
     * Sort for propertyPath ascending.
     *
     * @param propertyPath the propertyPath.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    static SortOptionEntry asc(final CharSequence propertyPath) {
        return ImmutableSortOptionEntry.asc(propertyPath);
    }

    /**
     * Sort for propertyPath descending.
     *
     * @param propertyPath the propertyPath.
     * @return the SortOptionEntry.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    static SortOptionEntry desc(final CharSequence propertyPath) {
        return ImmutableSortOptionEntry.desc(propertyPath);
    }

    /**
     * Returns the sort order of this entry.
     *
     * @return the sort order.
     */
    SortOrder getOrder();

    /**
     * Returns the path of the property which determines the sort order.
     *
     * @return the property path to be sorted after.
     */
    JsonPointer getPropertyPath();

    /**
     * Returns the string representation of this sort option entry. The string consists of the name of the sort order
     * followed by the property path. For example an ascending sort option entry for the property {@code "thingId"}
     * would yield {@code "+thingId"} as result.
     *
     * @return the string representation of this sort option entry.
     */
    @Override
    String toString();

    /**
     * An enumeration of sort orders.
     */
    enum SortOrder {

        /**
         * Represents an ascending sort order.
         */
        ASC("+"),

        /**
         * Represents a descending sort order.
         */
        DESC("-");

        private final String name;

        private SortOrder(final String name) {
            this.name = name;
        }

        /**
         * Returns the name of this sort order.
         *
         * @return the name of this sort order.
         */
        public String getName() {
            return name;
        }

    }

}
