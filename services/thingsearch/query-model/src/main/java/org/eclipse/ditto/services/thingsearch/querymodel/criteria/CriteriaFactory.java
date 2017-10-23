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

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;

/**
 * Builder for {@link Criteria}.
 */
public interface CriteriaFactory {

    /**
     * Creates a Criteria which matches any document.
     *
     * @return the criteria.
     */
    Criteria any();

    /**
     * Creates a Criteria which performs a logical AND of the given criterias.
     *
     * @param criterias the criterias.
     * @return the Criteria.
     */
    Criteria and(List<Criteria> criterias);

    /**
     * Creates a Criteria which performs a logical OR of the given criterias.
     *
     * @param criterias the criterias.
     * @return the Criteria.
     */
    Criteria or(List<Criteria> criterias);

    /**
     * Creates a Criteria which performs a logical NOR of the given criterias.
     *
     * @param criterias the criterias.
     * @return the Criteria.
     */
    Criteria nor(List<Criteria> criterias);

    /**
     * Creates a Criteria which performs a logical NOR of the given criteria.
     *
     * @param criteria the criteria.
     * @return the Criteria.
     * @throws NullPointerException if {@code criteria} is {@code null}.
     */
    default Criteria nor(final Criteria criteria) {
        return nor(Collections.singletonList(requireNonNull(criteria)));
    }

    /**
     * Creates a criteria to filter based on a certain field value.
     *
     * @param fieldExpression the filter expression which addresses the field.
     * @param predicate predicate which will be applied on the fieldExpression.
     * @return the criteria.
     */
    Criteria fieldCriteria(FilterFieldExpression fieldExpression, Predicate predicate);


    /**
     * Creates a criteria to filter based on the existence of the given field.
     *
     * @param fieldExpression the field to check for existence.
     * @return the criteria.
     */
    Criteria existsCriteria(ExistsFieldExpression fieldExpression);

    /**
     * Creates a predicate which checks for equality.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate eq(Object value);

    /**
     * Creates a predicate which checks for not equal.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate ne(Object value);

    /**
     * Creates a predicate which checks greater than.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate gt(Object value);

    /**
     * Creates a predicate which checks greater than or equals.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate ge(Object value);

    /**
     * Creates a predicate which checks lower than.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate lt(Object value);

    /**
     * Creates a predicate which checks lower than or equals.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate le(Object value);

    /**
     * Creates a predicate which checks lower than or equals.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate like(Object value);

    /**
     * The $in predicate selects the documents where the value of a field equals any value in the specified array.
     *
     * @param values the values.
     * @return the predicate.
     */
    Predicate in(List<?> values);

}
