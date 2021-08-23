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

import static java.util.Objects.requireNonNull;

import javax.annotation.Nullable;

/**
 * Generic comparison node that has a type, a property to compare on and a generic value. Subclasses have to specify
 * what type the value can have.
 *
 * @param <T> Type of the comparison enumeration.
 * @param <V> Type of the comparison value.
 */
abstract class ComparisonNode<T extends Enum<T>, V> implements Node {

    private final T comparisonType;
    private final String comparisonProperty;
    @Nullable private final V comparisonValue;

    /**
     * Constructor. Creates a new comparison node with the given type, property and value.
     *
     * @param comparisonType the type of the comparison.
     * @param comparisonProperty the property to compare on.
     * @param comparisonValue the value to compare for.
     */
    ComparisonNode(final T comparisonType, final String comparisonProperty, @Nullable final V comparisonValue) {
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
    @Nullable
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
            return other.comparisonValue == null;
        } else {
            return comparisonValue.equals(other.comparisonValue);
        }
    } // CS:ON equals(Object obj)
}
