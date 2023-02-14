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

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;

/**
 * Builder for {@link Criteria}.
 */
public interface CriteriaFactory {

    /**
     * Returns the CriteriaFactory instance.
     *
     * @return the CriteriaFactory instance.
     */
    static CriteriaFactory getInstance() {
        return CriteriaFactoryImpl.getInstance();
    }

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
    Predicate eq(@Nullable Object value);

    /**
     * Creates a predicate which checks for not equal.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate ne(@Nullable Object value);

    /**
     * Creates a predicate which checks greater than.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate gt(@Nullable Object value);

    /**
     * Creates a predicate which checks greater than or equals.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate ge(@Nullable Object value);

    /**
     * Creates a predicate which checks lower than.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate lt(@Nullable Object value);

    /**
     * Creates a predicate which checks lower than or equals.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate le(@Nullable Object value);

    /**
     * Represents a string 'like' comparison, supporting wildcards '*' for multiple and '?' for a single character.
     *
     * @param value the value, may be {@code null}.
     * @return the predicate.
     */
    Predicate like(@Nullable Object value);

    /**
     * Represents a string 'like' comparison, supporting wildcards '*' for multiple and '?' for a single character with case insensitivity.
     * @since 3.2.0
     * @param value the value, may be {@code null}.
     * @return the predicate. 
     */
    Predicate ilike(@Nullable Object value);
    
    /**
     * The $in predicate selects the documents where the value of a field equals any value in the specified array.
     *
     * @param values the values.
     * @return the predicate.
     */
    Predicate in(List<?> values);

}
