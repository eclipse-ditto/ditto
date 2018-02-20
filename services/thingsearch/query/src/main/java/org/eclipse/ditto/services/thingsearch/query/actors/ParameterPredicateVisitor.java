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
package org.eclipse.ditto.services.thingsearch.query.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.model.thingsearchparser.predicates.ast.ExistsNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.LogicalNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.MultiComparisonNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.Node;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.PredicateVisitor;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;

/**
 * Predicate AST PredicateVisitor. Implements the visitor for AST nodes and creates a search criteria out of it.
 */
final class ParameterPredicateVisitor implements PredicateVisitor {

    private static final Map<SingleComparisonNode.Type, BiFunction<CriteriaFactory, Object, Predicate>>
            SINGLE_COMPARISON_NODE_MAPPING;

    static {
        SINGLE_COMPARISON_NODE_MAPPING = new EnumMap<>(SingleComparisonNode.Type.class);
        SINGLE_COMPARISON_NODE_MAPPING.put(SingleComparisonNode.Type.EQ, CriteriaFactory::eq);
        SINGLE_COMPARISON_NODE_MAPPING.put(SingleComparisonNode.Type.NE, CriteriaFactory::ne);
        SINGLE_COMPARISON_NODE_MAPPING.put(SingleComparisonNode.Type.GT, CriteriaFactory::gt);
        SINGLE_COMPARISON_NODE_MAPPING.put(SingleComparisonNode.Type.GE, CriteriaFactory::ge);
        SINGLE_COMPARISON_NODE_MAPPING.put(SingleComparisonNode.Type.LT, CriteriaFactory::lt);
        SINGLE_COMPARISON_NODE_MAPPING.put(SingleComparisonNode.Type.LE, CriteriaFactory::le);
        SINGLE_COMPARISON_NODE_MAPPING.put(SingleComparisonNode.Type.LIKE, CriteriaFactory::like);
    }

    private final List<Criteria> criteria = new ArrayList<>();
    private final CriteriaFactory criteriaFactory;
    private final FieldExpressionFactory fieldExprFactory;

    /**
     * Constructs a new {@code ParameterPredicateVisitor} object.
     *
     * @param criteriaFactory the factory to create the search criteria.
     * @param fieldExprFactory the factory to create field expressions.
     * @throws NullPointerException if any argument is {@code null}.
     */
    ParameterPredicateVisitor(final CriteriaFactory criteriaFactory, final FieldExpressionFactory fieldExprFactory) {
        this.criteriaFactory = checkNotNull(criteriaFactory, "criteria factory");
        this.fieldExprFactory = checkNotNull(fieldExprFactory, "field expression factory");
    }

    /**
     * Retrieve all search criteria of this visitor.
     *
     * @return the criteria.
     */
    public List<Criteria> getCriteria() {
        return criteria;
    }

    @Override
    public void visit(final RootNode node) {
        node.getChildren().forEach(child -> child.accept(this));
    }

    @Override
    public void visit(final LogicalNode node) {
        checkNotNull(node, "logical node");
        final LogicalNode.Type type = node.getType();
        final ParameterPredicateVisitor childVisitor = new ParameterPredicateVisitor(criteriaFactory, fieldExprFactory);
        node.getChildren().forEach(child -> child.accept(childVisitor));

        switch (type) {
            case AND:
                criteria.add(criteriaFactory.and(childVisitor.getCriteria()));
                break;
            case OR:
                criteria.add(criteriaFactory.or(childVisitor.getCriteria()));
                break;
            case NOT:
                criteria.add(criteriaFactory.nor(childVisitor.getCriteria()));
                break;
            default:
                throwUnknownType(type);
        }
    }

    private static void throwUnknownType(final Object type) {
        throw new IllegalStateException("Unknown type: " + type);
    }

    @Override
    public void visit(final SingleComparisonNode node) {
        checkNotNull(node, "single comparison node");
        final FilterFieldExpression field = fieldExprFactory.filterBy(node.getComparisonProperty());
        final SingleComparisonNode.Type type = node.getComparisonType();
        final Object value = node.getComparisonValue();

        final BiFunction<CriteriaFactory, Object, Predicate> predicateFactory = SINGLE_COMPARISON_NODE_MAPPING.get(type);
        if (predicateFactory == null) {
            throwUnknownType(type);
        }

        final Predicate predicate = predicateFactory.apply(criteriaFactory, value);
        criteria.add(criteriaFactory.fieldCriteria(field, predicate));
    }

    @Override
    public void visit(final MultiComparisonNode node) {
        checkNotNull(node, "multi comparison node");
        final FilterFieldExpression field = fieldExprFactory.filterBy(node.getComparisonProperty());
        final MultiComparisonNode.Type type = node.getComparisonType();
        final List<Object> values = node.getComparisonValue();

        if (type == MultiComparisonNode.Type.IN) {
            criteria.add(criteriaFactory.fieldCriteria(field, criteriaFactory.in(values)));
        } else {
            throwUnknownType(type);
        }
    }

    @Override
    public void visit(final ExistsNode node) {
        checkNotNull(node, "exists node");
        final ExistsFieldExpression field = fieldExprFactory.existsBy(node.getProperty());
        criteria.add(criteriaFactory.existsCriteria(field));
    }

    @Override
    public void visit(final Node node) {
        // Do nothing, the other cases are handled by the more specific visit methods.
    }

}
