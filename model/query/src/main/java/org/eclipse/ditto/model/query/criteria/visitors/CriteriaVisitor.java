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
package org.eclipse.ditto.model.query.criteria.visitors;

import java.util.stream.Stream;

import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;

/**
 * Interface of compositional interpreters of search criteria ({@link Criteria}).
 */
public interface CriteriaVisitor<T> {

    T visitAnd(Stream<T> conjuncts);

    T visitAny();

    T visitExists(ExistsFieldExpression fieldExpression);

    T visitField(FilterFieldExpression fieldExpression, Predicate predicate);

    T visitNor(Stream<T> negativeDisjoints);

    T visitOr(Stream<T> disjoints);

}
