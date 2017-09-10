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
package org.eclipse.ditto.model.thingsearchparser.predicates.rql;

import org.eclipse.ditto.model.thingsearchparser.BaseRules;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.ExistsNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.LogicalNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.MultiComparisonNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.Node;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.SuperNode;
import org.parboiled.Rule;

/**
 * RQL rule definitions for the predicates.
 */
class PredicateRules extends BaseRules {
    // -------------------------------------------------------------------------
    // Rule Definitions
    // -------------------------------------------------------------------------

    private static final int POP_COUNT = 2;

    /**
     * Defines the root rule that adds a {@link RootNode} and parses the query until {@see EOI}.
     *
     * @return the root rule
     */
    @Override
    public Rule root() {
        return Sequence(push(new RootNode()), query(), EOI);
    }

    /**
     * Defines a rule to parse single queries.
     *
     * @return rule that matches queries.
     */
    Rule query() {
        return Sequence(
                FirstOf(multiLogicalOp(), singleLogicalOp(), singleComparisonOp(), multiComparisonOp(), existsOp()),
                addAsChild());
    }

    // -------------------------------------------------------------------------
    // Logical Operations
    // -------------------------------------------------------------------------

    Rule multiLogicalOp() {
        return Sequence(logicalName(), push(new LogicalNode((LogicalNode.Type) pop())), "(", query(),
                Optional(OneOrMore(Sequence(",", query()))), ")");
    }

    Rule logicalName() {
        return FirstOf( //
                Sequence(String("and"), push(LogicalNode.Type.and)), //
                Sequence(String("or"), push(LogicalNode.Type.or)));
    }

    Rule singleLogicalOp() {
        // there is only one logical operation with a single sub query and that is NOT
        return Sequence(String("not"), push(new LogicalNode(LogicalNode.Type.not)), "(", query(), ")");
    }

    // -------------------------------------------------------------------------
    // Comparison Operations - commons
    // -------------------------------------------------------------------------

    Rule comparisonProperty() {
        return propertyLiteral();
    }

    // -------------------------------------------------------------------------
    // Comparison Operations with a single value
    // -------------------------------------------------------------------------

    Rule singleComparisonOp() {
        return Sequence(singleComparisonName(), '(', comparisonProperty(), ',', singleComparisonValue(), ')',
                push(new SingleComparisonNode((SingleComparisonNode.Type) pop(POP_COUNT), popAsString(1), pop())));
    }

    Rule singleComparisonName() {
        return FirstOf( //
                Sequence(String("eq"), push(SingleComparisonNode.Type.eq)), //
                Sequence(String("ne"), push(SingleComparisonNode.Type.ne)), //
                Sequence(String("gt"), push(SingleComparisonNode.Type.gt)), //
                Sequence(String("ge"), push(SingleComparisonNode.Type.ge)), //
                Sequence(String("lt"), push(SingleComparisonNode.Type.lt)), //
                Sequence(String("le"), push(SingleComparisonNode.Type.le)), //
                Sequence(String("like"), push(SingleComparisonNode.Type.like)));
    }

    Rule singleComparisonValue() {
        return literal();
    }

    // -------------------------------------------------------------------------
    // Comparison Operations with a value array
    // -------------------------------------------------------------------------

    Rule existsOp() {
        return Sequence(String("exists"), '(', propertyLiteral(), push(new ExistsNode(popAsString(0))), ')');
    }

    Rule multiComparisonOp() {
        return Sequence(multiComparisonName(), '(', comparisonProperty(),
                push(new MultiComparisonNode((MultiComparisonNode.Type) pop(1), popAsString(0))),
                multiComparisonValues(),
                ')');
    }

    Rule multiComparisonName() {
        return Sequence(String("in"), push(MultiComparisonNode.Type.in));
    }

    Rule multiComparisonValues() {
        return OneOrMore(Sequence(',', literal(), addValue()));
    }


    // -------------------------------------------------------------------------
    // Helper methods to work with the value stack
    // -------------------------------------------------------------------------

    /**
     * Removes the value at the top of the value stack and returns it as a node.
     *
     * @return the value as node
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    Node popAsNode() {
        return (Node) pop();
    }

    /**
     * Retrieves the top node of the value stack and adds it to the next node on the value stack as child.
     *
     * @return true in order for parboiled to continue processing (otherwise we cannot use this mehtod inline).
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    boolean addAsChild() {
        final Node child = popAsNode();
        final SuperNode parent = (SuperNode) peek();
        parent.getChildren().add(child);
        return true;
    }

    /**
     * Retrieves the top from the value stack which should be a parsed array value and puts it in the parent which
     * should be of type {@link MultiComparisonNode}.
     *
     * @return true in order for parboiled to continue processing (otherwise we cannot use this mehtod inline).
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    boolean addValue() {
        final Object value = pop();
        final MultiComparisonNode node = (MultiComparisonNode) peek();
        node.addValue(value);
        return true;
    }

    // -------------------------------------------------------------------------
    // Helper methods to work with the value stack
    // -------------------------------------------------------------------------

    /**
     * Removes the value the given number of elements below the top of the value stack and returns it as a string.
     *
     * @param down the number of elements to skip before removing the value (0 being equivalent to pop())
     * @return the value as string
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    String popAsString(final int down) {
        return (String) pop(down);
    }
}
