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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.model.thingsearchparser.predicates.ast.LogicalNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.SingleComparisonNode;
import org.junit.Before;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionUtil;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;

/**
 * Unit test for {@link ParameterPredicateVisitor}.
 */
public final class ParameterPredicateVisitorTest {

    private static final String KNOWN_FIELD_NAME = FieldExpressionUtil.FIELD_NAME_THING_ID;
    private static final String KNOWN_FIELD_VALUE_1 = "value1";
    private static final String KNOWN_FIELD_VALUE_2 = "value2";

    private ParameterPredicateVisitor visitorUnderTest;
    private CriteriaFactoryImpl cf;
    private ThingsFieldExpressionFactoryImpl ef;

    @Before
    public void before() {
        cf = new CriteriaFactoryImpl();
        ef = new ThingsFieldExpressionFactoryImpl();
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

    /** */
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
        assertThat(visitorUnderTest.getCriteria()).isEqualTo(Arrays.asList(fieldCrit1, fieldCrit2));
    }

    /** */
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

        visitorUnderTest.getCriteria().get(0).equals(cf.and(Arrays.asList(fieldCrit1, fieldCrit2)));

        assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.and(Arrays.asList(fieldCrit1, fieldCrit2)));
    }

    /** */
    @Test
    public void logicalNodeAnd() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.AND);

        visitorUnderTest.visit(logicalNode);

        assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.and(Collections.emptyList()));
    }

    /** */
    @Test
    public void logicalNodeOr() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.OR);

        visitorUnderTest.visit(logicalNode);

        assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.or(Collections.emptyList()));
    }

    /** */
    @Test
    public void logicalNodeNot() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.NOT);
        visitorUnderTest.visit(logicalNode);

        assertThat(visitorUnderTest.getCriteria()).containsExactly(cf.nor(Collections.emptyList()));
    }

    /** */
    @Test
    public void filterNodeEq() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.eq(KNOWN_FIELD_VALUE_1));
        assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    /** */
    @Test
    public void filterNodeNe() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.NE, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.ne(KNOWN_FIELD_VALUE_1));
        assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    /** */
    @Test
    public void filterNodeGt() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.GT, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.gt(KNOWN_FIELD_VALUE_1));
        assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    /** */
    @Test
    public void filterNodeGe() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.GE, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.ge(KNOWN_FIELD_VALUE_1));
        assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    /** */
    @Test
    public void filterNodeLt() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.LT, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.lt(KNOWN_FIELD_VALUE_1));
        assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

    /** */
    @Test
    public void filterNodeLe() {
        final SingleComparisonNode filterNode =
                new SingleComparisonNode(SingleComparisonNode.Type.LE, KNOWN_FIELD_NAME, KNOWN_FIELD_VALUE_1);

        visitorUnderTest.visit(filterNode);

        final Criteria expectedCrit = cf.fieldCriteria(ef.filterBy(KNOWN_FIELD_NAME), cf.le(KNOWN_FIELD_VALUE_1));
        assertThat(visitorUnderTest.getCriteria()).containsExactly(expectedCrit);
    }

}
