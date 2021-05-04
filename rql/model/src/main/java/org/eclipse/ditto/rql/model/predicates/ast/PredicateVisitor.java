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


/**
 * Interface for the visitor pattern to traverse through the AST.
 */
public interface PredicateVisitor {

    /**
     * Is called by a {@link RootNode} in its {@link RootNode#accept(PredicateVisitor)} method.
     *
     * @param node an instance of the {@link RootNode}
     */
    void visit(RootNode node);

    /**
     * Is called by a {@link LogicalNode} in its {@link LogicalNode#accept(PredicateVisitor)} method.
     *
     * @param node an instance of the {@link LogicalNode}
     */
    void visit(LogicalNode node);

    /**
     * Is called by a {@link SingleComparisonNode} in its {@link SingleComparisonNode#accept(PredicateVisitor)} method.
     *
     * @param node an instance of the {@link SingleComparisonNode}
     */
    void visit(SingleComparisonNode node);

    /**
     * Is called by a {@link MultiComparisonNode} in its {@link MultiComparisonNode#accept(PredicateVisitor)} method.
     *
     * @param node an instance of the {@link MultiComparisonNode}
     */
    void visit(MultiComparisonNode node);

    /**
     * Is called by a {@link ExistsNode} in its {@link ExistsNode#accept(PredicateVisitor)} method.
     *
     * @param node an instance of the {@link ExistsNode}
     */
    void visit(ExistsNode node);

    /**
     * Is called by a {@link Node} in its {@link Node#accept(PredicateVisitor)} method.
     *
     * @param node an instance of the {@link Node}
     */
    void visit(Node node); // general catch all for custom Node implementations
}
