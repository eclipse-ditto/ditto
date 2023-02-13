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
package org.eclipse.ditto.rql.query.criteria;

import static java.util.Objects.requireNonNull;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;

/**
 * Class for creating queries.
 */
final class CriteriaFactoryImpl implements CriteriaFactory {

    private static final CriteriaFactoryImpl INSTANCE = new CriteriaFactoryImpl();

    private CriteriaFactoryImpl() {
        // private
    }

    /**
     * Returns the CriteriaFactoryImpl instance.
     *
     * @return the CriteriaFactoryImpl instance.
     */
    static CriteriaFactoryImpl getInstance() {
       return INSTANCE;
    }

    @Override
    public Criteria any() {
        return AnyCriteriaImpl.getInstance();
    }

    @Override
    public Criteria and(final List<Criteria> criterias) {
        return new AndCriteriaImpl(requireNonNull(criterias));
    }

    @Override
    public Criteria or(final List<Criteria> criterias) {
        return new OrCriteriaImpl(requireNonNull(criterias));
    }

    @Override
    public Criteria nor(final List<Criteria> criterias) {
        return new NorCriteriaImpl(requireNonNull(criterias));
    }

    @Override
    public Criteria fieldCriteria(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return new FieldCriteriaImpl(requireNonNull(fieldExpression), requireNonNull(predicate));
    }

    @Override
    public Criteria existsCriteria(final ExistsFieldExpression fieldExpression) {
        return new ExistsCriteriaImpl(requireNonNull(fieldExpression));
    }

    @Override
    public Predicate eq(@Nullable final Object value) {
        return new EqPredicateImpl(value);
    }

    @Override
    public Predicate ne(@Nullable final Object value) {
        return new NePredicateImpl(value);
    }

    @Override
    public Predicate gt(@Nullable final Object value) {
        return new GtPredicateImpl(value);
    }

    @Override
    public Predicate ge(@Nullable final Object value) {
        return new GePredicateImpl(value);
    }

    @Override
    public Predicate lt(@Nullable final Object value) {
        return new LtPredicateImpl(value);
    }

    @Override
    public Predicate le(@Nullable final Object value) {
        return new LePredicateImpl(value);
    }

    @Override
    public Predicate like(@Nullable final Object value) {
        if (value instanceof String) {
            return new LikePredicateImpl(value);
        } else {
            throw new IllegalArgumentException("In the like predicate only string values are allowed.");
        }
    }

    @Override
    public Predicate ilike(@Nullable final Object value) {
        if (value instanceof String) {
            return new ILikePredicateImpl(value);
        } else {
            throw new IllegalArgumentException("In the ilike predicate only string values are allowed.");
        }
    }

    @Override
    public Predicate in(final List<?> values) {
        return new InPredicateImpl(requireNonNull(values));
    }

}
