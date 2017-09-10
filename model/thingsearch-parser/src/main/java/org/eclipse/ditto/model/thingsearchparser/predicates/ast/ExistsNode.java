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

import java.util.Objects;

/**
 * Implements an exists node. Such a node only has one parameter that is the field name to check for existence.
 */
public class ExistsNode implements Node {

    private final String property;

    /**
     * Constructor. Creates a new node with the given property.
     *
     * @param property property of this exists node.
     */
    public ExistsNode(final String property) {
        this.property = requireNonNull(property);
    }

    /**
     * Retrieve the property of this exists node.
     *
     * @return the property of the exists node.
     */
    public String getProperty() {
        return property;
    }

    @Override
    public void accept(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExistsNode that = (ExistsNode) o;
        return Objects.equals(property, that.property);
    }

    @Override
    @SuppressWarnings("squid:S109")
    public int hashCode() {
        return Objects.hash(property);
    }

    @Override
    public String toString() {
        return "ExistsNode [property=" + property + "]";
    }
}
