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
package org.eclipse.ditto.rql.model.predicates.ast;


import javax.annotation.Nullable;

/**
 * Implements a comparison node like EQ (equals). A comparison node has a name, a property to compare on and a single
 * value to compare for.
 */
public final class SingleComparisonNode extends ComparisonNode<SingleComparisonNode.Type, Object> {

    /**
     * Constructor. Creates a new comparison node with the given type, property and value.
     *
     * @param comparisonType the type of the comparison.
     * @param comparisonProperty the property to compare on.
     * @param comparisonValue the value to compare for.
     */
    public SingleComparisonNode(final Type comparisonType, final String comparisonProperty,
            @Nullable final Object comparisonValue) {
        super(comparisonType, comparisonProperty, comparisonValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(final PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    /**
     * Defines the possible types that a {@link SingleComparisonNode} can have.
     */
    public enum Type {
        /**
         * Represents an equals comparison.
         */
        EQ("eq"),

        /**
         * Represents a not equal comparison.
         */
        NE("ne"),

        /**
         * Represents a greater than comparison.
         */
        GT("gt"),

        /**
         * Represents a greater than or equals comparison.
         */
        GE("ge"),

        /**
         * Represents a lower than comparison.
         */
        LT("lt"),

        /**
         * Represents a lower than or equals comparison.
         */
        LE("le"),

        /**
         * Represents a string 'like' comparison, supporting wildcards '*' for multiple and '?' for a single character.
         */
        LIKE("like"),
        
         /**
         * Represents a string 'like' comparison, supporting wildcards '*' for multiple and '?' for a single character with case sensitivity.
         */
        ILIKE("ilike");

        private final String name;

        Type(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
