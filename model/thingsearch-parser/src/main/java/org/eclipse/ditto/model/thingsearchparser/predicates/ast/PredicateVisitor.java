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
