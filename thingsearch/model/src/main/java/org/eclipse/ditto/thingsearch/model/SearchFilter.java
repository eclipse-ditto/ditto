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

import javax.annotation.concurrent.Immutable;

/**
 * A search filter is the crucial input of a {@link SearchQuery}. This comprises the name of the searched property, the
 * type of the filter operation like EQ but also logical operations like AND.
 */
@Immutable
public interface SearchFilter {

    /**
     * Returns the type of this filter.
     *
     * @return the type of this filter.
     */
    Type getType();

    /**
     * Returns the string representation of this filter. The string format is defined by each implementation of this
     * interface.
     *
     * @return the string representation of this filter.
     */
    @Override
    String toString();

    /**
     * An enumeration of the various filter types.
     *
     */
    enum Type {

        /**
         * Filter type for concatenating two or more filters with the boolean AND operator.
         */
        AND("and"),


        /**
         * Filter type for concatenating two or more filters with the boolean OR operator.
         */
        OR("or"),

        /**
         * Filter type for negating a filter.
         */
        NOT("not"),

        /**
         * Filter type for checking if a entity exists.
         */
        EXISTS("exists"),

        /**
         * Filter type for checking if two entities are equal.
         */
        EQ("eq"),

        /**
         * Filter type for checking if two entities are not equal.
         */
        NE("ne"),

        /**
         * Filter type for checking if one entity is greater than another entity.
         */
        GT("gt"),

        /**
         * Filter type for checking if one entity is greater than OR equal to another entity.
         */
        GE("ge"),

        /**
         * Filter type for checking if one entity is less than another entity.
         */
        LT("lt"),

        /**
         * Filter type for checking if one entity is less than OR equal to another entity.
         */
        LE("le"),

        /**
         * Filter type for checking if a string matches a regular expression.
         */
        LIKE("like"),

        /**
         * Filter type for checking if a string matches a regular expression, in a case insensitive way.
         */
        ILIKE("ilike"),

        /**
         * Filter type for checking if an entity is contained in a set of given entities.
         */
        IN("in");

        private final String name;

        Type(final String name) {
            this.name = name;
        }

        /**
         * Returns the name of this filter type.
         *
         * @return the name of this type.
         */
        public String getName() {
            return name;
        }
    }

}
