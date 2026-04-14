/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.Objects;

/**
 * Implements an empty node. Such a node only has one parameter that is the field name to check for emptiness.
 * A field is considered "empty" when it is absent, {@code null}, an empty array {@code []}, an empty object
 * {@code {}} or an empty string {@code ""}.
 *
 * @since 3.9.0
 */
public class EmptyNode implements Node {

    private final String property;

    /**
     * Constructor. Creates a new node with the given property.
     *
     * @param property property of this empty node.
     */
    public EmptyNode(final String property) {
        this.property = requireNonNull(property);
    }

    /**
     * Retrieve the property of this empty node.
     *
     * @return the property of the empty node.
     */
    public String getProperty() {
        return property;
    }

    @Override
    public void accept(final PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EmptyNode that = (EmptyNode) o;
        return Objects.equals(property, that.property);
    }

    @Override
    public int hashCode() {
        return Objects.hash(property);
    }

    @Override
    public String toString() {
        return "EmptyNode [property=" + property + "]";
    }
}
