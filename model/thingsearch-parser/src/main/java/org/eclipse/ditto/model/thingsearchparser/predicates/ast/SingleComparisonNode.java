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
package org.eclipse.ditto.model.thingsearchparser.predicates.ast;


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
            final Object comparisonValue) {
        super(comparisonType, comparisonProperty, comparisonValue);
    }

    /**
     * {@inheritDoc}
     */
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
        eq("eq"),

        /**
         * Represents a not equal comparison.
         */
        ne("ne"),

        /**
         * Represents a greater than comparison.
         */
        gt("gt"),

        /**
         * Represents a greater than or equals comparison.
         */
        ge("ge"),

        /**
         * Represents a lower than comparison.
         */
        lt("lt"),

        /**
         * Represents a lower than or equals comparison.
         */
        le("le"),

        /**
         * Represents a lower than or equals comparison.
         */
        like("like");

        private final String name;

        Type(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
