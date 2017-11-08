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
package org.eclipse.ditto.services.thingsearch.querymodel.criteria;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;


/**
 * Class for creating queries.
 */
public final class CriteriaFactoryImpl implements CriteriaFactory {

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
        return new FieldCriteriaImpl(requireNonNull(fieldExpression), requireNonNull((Predicate) predicate));
    }

    @Override
    public Criteria existsCriteria(final ExistsFieldExpression fieldExpression) {
        return new ExistsCriteriaImpl(requireNonNull(fieldExpression));
    }

    @Override
    public Predicate eq(final Object value) {
        return new EqPredicateImpl(value);
    }

    @Override
    public Predicate ne(final Object value) {
        return new NePredicateImpl(value);
    }

    @Override
    public Predicate gt(final Object value) {
        return new GtPredicateImpl(value);
    }

    @Override
    public Predicate ge(final Object value) {
        return new GePredicateImpl(value);
    }

    @Override
    public Predicate lt(final Object value) {
        return new LtPredicateImpl(value);
    }

    @Override
    public Predicate le(final Object value) {
        return new LePredicateImpl(value);
    }

    @Override
    public Predicate like(final Object value) {
        if (value instanceof String) {
            return new LikePredicateImpl(value);
        } else {
            throw new IllegalArgumentException("In the like predicate only string values are allowed.");
        }
    }

    @Override
    public Predicate in(final List<?> values) {
        return new InPredicateImpl(requireNonNull(values));
    }

}
