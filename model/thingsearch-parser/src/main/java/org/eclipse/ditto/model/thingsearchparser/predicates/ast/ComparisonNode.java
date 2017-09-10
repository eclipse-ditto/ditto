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

import static java.util.Objects.requireNonNull;

/**
 * Generic comparison node that has a type, a property to compare on and a generic value. Subclasses have to specifiy
 * what type the value can have.
 *
 * @param <T> Type of the comparison enumeration.
 * @param <V> Type of the comparison value.
 */
abstract class ComparisonNode<T extends Enum<T>, V> implements Node {

    private final T comparisonType;
    private final V comparisonValue;
    private final String comparisonProperty;

    /**
     * Constructor. Creates a new comparison node with the given type, property and value.
     *
     * @param comparisonType the type of the comparison.
     * @param comparisonProperty the property to compare on.
     * @param comparisonValue the value to compare for.
     */
    ComparisonNode(final T comparisonType, final String comparisonProperty, final V comparisonValue) {
        super();
        this.comparisonType = comparisonType;
        this.comparisonProperty = requireNonNull(comparisonProperty);
        this.comparisonValue = comparisonValue;
    }

    /**
     * Retrieve the type of the comparison.
     *
     * @return the type of the comparison.
     */
    public T getComparisonType() {
        return comparisonType;
    }

    /**
     * Retrieve the property to compare on.
     *
     * @return the property to compare on.
     */
    public String getComparisonProperty() {
        return comparisonProperty;
    }

    /**
     * Retrieve the value to compare for.
     *
     * @return the value to compare for.
     */
    public V getComparisonValue() {
        return comparisonValue;
    }

    /**
     * {@inheritDoc}
     */
    public void accept(final PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "ComparisonNode [comparisonType=" + comparisonType + ", comparisonValue=" + comparisonValue
                + ", comparisonProperty=" + comparisonProperty + "]";
    }

    // CS:OFF generated
    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((comparisonProperty == null) ? 0 : comparisonProperty.hashCode());
        result = (prime * result) + ((comparisonType == null) ? 0 : comparisonType.hashCode());
        result = (prime * result) + ((comparisonValue == null) ? 0 : comparisonValue.hashCode());
        return result;
    } // CS:ON hashCode()

    // CS:OFF generated
    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ComparisonNode<?, ?> other = (ComparisonNode<?, ?>) obj;
        if (comparisonProperty == null) {
            if (other.comparisonProperty != null) {
                return false;
            }
        } else if (!comparisonProperty.equals(other.comparisonProperty)) {
            return false;
        }
        if (comparisonType == null) {
            if (other.comparisonType != null) {
                return false;
            }
        } else if (!comparisonType.equals(other.comparisonType)) {
            return false;
        }
        if (comparisonValue == null) {
            if (other.comparisonValue != null) {
                return false;
            }
        } else if (!comparisonValue.equals(other.comparisonValue)) {
            return false;
        }
        return true;
    } // CS:ON equals(Object obj)
}
