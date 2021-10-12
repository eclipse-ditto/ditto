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
package org.eclipse.ditto.rql.query.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.rql.model.predicates.ast.LogicalNode;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.model.predicates.ast.SingleComparisonNode;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ParameterPredicateVisitor}.
 */
public final class ParameterPredicateVisitorTest {

    private static final String KNOWN_FIELD_NAME = FieldExpressionUtil.FIELD_NAME_THING_ID;
    private static final String KNOWN_FIELD_VALUE_1 = "value1";
    private static final String KNOWN_FIELD_VALUE_2 = "value2";

    private ParameterPredicateVisitor visitorUnderTest;
    private CriteriaFactory cf;
    private ThingsFieldExpressionFactory ef;

    @Before
    public void before() {
        cf = CriteriaFactory.getInstance();

        final Map<String, String> simpleFieldMappings = new HashMap<>();
        simpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        simpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
        ef = ThingsFieldExpressionFactory.of(simpleFieldMappings);
        visitorUnderTest = new ParameterPredicateVisitor(cf, ef);
    }

    @Test(expected = NullPointerException.class)
    public void constructorWithNullCf() {
        new ParameterPredicateVisitor(null, ef);
    }

    @Test(expected = NullPointerException.class)
    public void constructorWithNullEf() {
        new ParameterPredicateVisitor(cf, null);
    }

    @Test
    public void visitChildrenOfRootNode() {
        final RootNode rootNode = new RootNode();

        final SingleComparisonNode filterNode1 =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);
        final SingleComparisonNode filterNode2 =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_2);

        rootNode.getChildren().add(filterNode1);
        rootNode.getChildren().add(filterNode2);

        visitorUnderTest.visit(rootNode);

        final Criteria fieldCrit1 = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.eq(KNOWN_FIELD_VALUE_1));
        final Criteria fieldCrit2 = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.eq(KNOWN_FIELD_VALUE_2));
        Assertions.assertThat(visitorUnderTest.getCriteria()).isEqualTo(Arrays.asList(fieldCrit1, fieldCrit2));
    }

    @Test
    public void visitChildrenOfLogicalNode() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.AND);

        final SingleComparisonNode filterNode1 =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);
        final SingleComparisonNode filterNode2 =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_2);

        logicalNode.getChildren().add(filterNode1);
        logicalNode.getChildren().add(filterNode2);

        visitorUnderTest.visit(logicalNode);

        final Criteria fieldCrit1 = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.eq(KNOWN_FIELD_VALUE_1));
        final Criteria fieldCrit2 = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.eq(KNOWN_FIELD_VALUE_2));

        Assertions.assertThat(visitorUnderTest.getCriteria().get(0)).isEqualTo(
                cf.and(Arrays.asList(fieldCrit1, fieldCrit2)));

        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.and(Arrays.asList(fieldCrit1, fieldCrit2)));
    }

    @Test
    public void logicalNodeAnd() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.AND);

        visitorUnderTest.visit(logicalNode);

        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.and(Collections.emptyList()));
    }

    @Test
    public void logicalNodeOr() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.OR);

        visitorUnderTest.visit(logicalNode);

        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.or(Collections.emptyList()));
    }

    @Test
    public void logicalNodeNot() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.NOT);
        visitorUnderTest.visit(logicalNode);

        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.nor(Collections.emptyList()));
    }

    @Test
    public void filterNodeEq() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.eq(KNOWN_FIELD_VALUE_1));
        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    @Test
    public void filterNodeNe() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.NE, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.ne(KNOWN_FIELD_VALUE_1));
        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    @Test
    public void filterNodeGt() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.GT, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.gt(KNOWN_FIELD_VALUE_1));
        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    @Test
    public void filterNodeGe() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.GE, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.ge(KNOWN_FIELD_VALUE_1));
        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    @Test
    public void filterNodeLt() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.LT, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.lt(KNOWN_FIELD_VALUE_1));
        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    @Test
    public void filterNodeLe() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.LE, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.le(KNOWN_FIELD_VALUE_1));
        Assertions.assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

}
