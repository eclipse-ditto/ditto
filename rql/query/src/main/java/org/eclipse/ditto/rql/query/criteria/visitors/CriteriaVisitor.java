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
package org.eclipse.ditto.rql.query.criteria.visitors;

import java.util.List;

import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;

/**
 * Interface of compositional interpreters of search criteria ({@link Criteria}).
 *
 * @param <T> the type to create the criterias for.
 */
public interface CriteriaVisitor<T> {

    /**
     * Visits the passed {@code conjuncts} applying "and" semantics.
     *
     * @param conjuncts the streamed parts to apply "and" on.
     * @return the created thingy.
     */
    T visitAnd(List<T> conjuncts);

    /**
     * Visits applying "any" semantics.
     *
     * @return the created thingy.
     */
    T visitAny();

    /**
     * Visits the passed {@code fieldExpression} applying "exists" semantics.
     *
     * @param fieldExpression the expression to check existence for.
     * @return the created thingy.
     */
    T visitExists(ExistsFieldExpression fieldExpression);

    /**
     * Visits the passed {@code fieldExpression} and {@code predicate} applying "filtering" semantics.
     *
     * @param fieldExpression the expression to check.
     * @param predicate the predicate to check.
     * @return the created thingy.
     */
    T visitField(FilterFieldExpression fieldExpression, Predicate predicate);

    /**
     * Visits the passed {@code negativeDisjoints} applying "nor" semantics.
     *
     * @param negativeDisjoints the streamed parts to apply "nor" on.
     * @return the created thingy.
     */
    T visitNor(List<T> negativeDisjoints);

    /**
     * Visits the passed {@code disjoints} applying "or" semantics.
     *
     * @param disjoints the streamed parts to apply "or" on.
     * @return the created thingy.
     */
    T visitOr(List<T> disjoints);

}
