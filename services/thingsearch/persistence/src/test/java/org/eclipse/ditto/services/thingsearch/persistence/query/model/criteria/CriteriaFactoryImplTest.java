/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.query.model.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.query.criteria.AndCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.AnyCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.criteria.EqPredicateImpl;
import org.eclipse.ditto.model.query.criteria.ExistsCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.FieldCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.InPredicateImpl;
import org.eclipse.ditto.model.query.criteria.NePredicateImpl;
import org.eclipse.ditto.model.query.criteria.NorCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.OrCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.junit.Test;
/**
 * Tests {@link CriteriaFactoryImpl}.
 */
public final class CriteriaFactoryImplTest {

    private static final String KNOWN_STRING = "KNOWN_STRING";
    private final CriteriaFactory cf = new CriteriaFactoryImpl();

    private static List<Criteria> getMockedCriteria() {
        return Arrays.asList(mock(Criteria.class), mock(Criteria.class));
    }

    /** */
    @Test
    public void any() {
        final Criteria any = cf.any();
        assertThat(any).isNotNull().isInstanceOf(AnyCriteriaImpl.class);
    }

    /** */
    @Test
    public void and() {
        final Criteria and = cf.and(getMockedCriteria());
        assertThat(and).isNotNull().isInstanceOf(AndCriteriaImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void andWithNullCriteria() {
        cf.and(null);
    }

    /** */
    @Test
    public void or() {
        final Criteria or = cf.or(getMockedCriteria());
        assertThat(or).isNotNull().isInstanceOf(OrCriteriaImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void orWithNullCriteria() {
        cf.or(null);
    }

    /** */
    @Test
    public void nor() {
        final Criteria nor = cf.nor(getMockedCriteria());
        assertThat(nor).isNotNull().isInstanceOf(NorCriteriaImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void norWithNullCriterias() {
        cf.nor((List<Criteria>) null);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void norWithNullCriteria() {
        cf.nor((Criteria) null);
    }

    /** */
    @Test
    public void fieldCriteria() {
        final Criteria fieldCriteria =
                cf.fieldCriteria(mock(FilterFieldExpression.class), mock(Predicate.class));
        assertThat(fieldCriteria).isNotNull().isInstanceOf(FieldCriteriaImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void fieldCriteriaWithNullExpression() {
        cf.fieldCriteria(null, mock(Predicate.class));
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void fieldCriteriaWithNullPredicate() {
        cf.fieldCriteria(mock(FilterFieldExpression.class), null);
    }

    /** */
    @Test
    public void existsCriteria() {
        final Criteria existsCriteria = cf.existsCriteria(mock(ExistsFieldExpression.class));
        assertThat(existsCriteria).isNotNull().isInstanceOf(ExistsCriteriaImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void existsCriteriaWithNullExpression() {
        cf.existsCriteria(null);
    }

    /** */
    @Test
    public void eq() {
        eq(KNOWN_STRING);
    }

    /** */
    @Test
    public void eqWithNullValue() {
        eq(null);
    }

    private void eq(final String value) {
        final Predicate predicate = cf.eq(value);
        assertThat(predicate).isNotNull().isInstanceOf(EqPredicateImpl.class);
    }

    /** */
    @Test
    public void ne() {
        ne(KNOWN_STRING);
    }

    /** */
    @Test
    public void neWithNullValue() {
        ne(null);
    }

    private void ne(final String value) {
        final Predicate predicate = cf.ne(value);
        assertThat(predicate).isNotNull().isInstanceOf(NePredicateImpl.class);
    }

    /** */
    @Test
    public void in() {
        final Predicate in = cf.in(Arrays.asList(KNOWN_STRING));
        assertThat(in).isNotNull().isInstanceOf(InPredicateImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void inWithNullValue() {
        cf.in(null);
    }
}
