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

import org.eclipse.ditto.json.JsonPointer;

/**
 * A particular entry of the sort option. For example {@code +thingId} is an entry which conveys the meaning that the
 * Thing ID should be sorted in ascending order.
 */
public interface SortOptionEntry {

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
