/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.rql.predicates.ast;

import java.util.ArrayList;
import java.util.List;


/**
 * Implements a comparison node like IN (in array). A comparison node has a name, a property to compare on and a array
 * of values to compare for.
 */
public final class MultiComparisonNode extends ComparisonNode<MultiComparisonNode.Type, List<Object>> {

    /**
     * Constructor. Creates a new comparison node with the given type, property and list of values.
     *
     * @param comparisonType the type of the comparison.
     * @param comparisonProperty the property to compare on.
     */
    public MultiComparisonNode(final Type comparisonType, final String comparisonProperty) {
        this(comparisonType, comparisonProperty, new ArrayList<>());
    }

    /**
     * Constructor. Creates a new comparison node with the given type, property and list of values.
     *
     * @param comparisonType the type of the comparison.
     * @param comparisonProperty the property to compare on.
     * @param comparisonPropertyValues the property to compare on.
     */
    public MultiComparisonNode(final Type comparisonType, final String comparisonProperty,
            final List<Object> comparisonPropertyValues) {
        super(comparisonType, comparisonProperty, comparisonPropertyValues);
    }

    /**
     * Add the given value to the list of values.
     *
     * @param value the value to add.
     */
    public void addValue(final Object value) {
        this.getComparisonValue().add(value);
    }

    /**
     * {@inheritDoc}
     */
    public void accept(final PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    /**
     * Defines the possible types that a {@link MultiComparisonNode} can have.
     */
    public enum Type {
        /**
         * Represents an in comparison.
         */
        IN("in");

        private final String name;

        Type(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
