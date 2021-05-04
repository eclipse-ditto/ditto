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

import java.util.ArrayList;
import java.util.List;

/**
 * A super node is a node in the AST that can have other nodes as children.
 */
public abstract class SuperNode implements Node {

    private final List<Node> children;

    /**
     * Constructor. Creates an instance of a super node.
     */
    protected SuperNode() {
        children = new ArrayList<>();
    }

    /**
     * Retrieve all children of this super node.
     *
     * @return a list of all children.
     */
    public List<Node> getChildren() {
        return children;
    }

    /**
     * {@inheritDoc}
     */
    public void accept(final PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "SuperNode [children=" + children + "]";
    }

    // CS:OFF generated
    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((children == null) ? 0 : children.hashCode());
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
        final SuperNode other = (SuperNode) obj;
        if (children == null) {
            if (other.children != null) {
                return false;
            }
        } else if (!children.equals(other.children)) {
            return false;
        }
        return true;
    } // cS:ON equals()
}
