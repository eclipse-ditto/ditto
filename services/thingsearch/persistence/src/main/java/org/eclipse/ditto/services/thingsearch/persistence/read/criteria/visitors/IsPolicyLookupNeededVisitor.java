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
package org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors;

import java.util.stream.Stream;

import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.PolicyRestrictedFieldExpression;

/**
 * Check whether a filter criteria requires policy lookup.
 */
public final class IsPolicyLookupNeededVisitor implements CriteriaVisitor<Boolean> {

    @Override
    public Boolean visitAnd(final Stream<Boolean> conjuncts) {
        return conjuncts.anyMatch(x -> x);
    }

    @Override
    public Boolean visitAny() {
        return false;
    }

    @Override
    public Boolean visitExists(final ExistsFieldExpression fieldExpression) {
        return fieldExpression instanceof PolicyRestrictedFieldExpression;
    }

    @Override
    public Boolean visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return fieldExpression instanceof PolicyRestrictedFieldExpression;
    }

    @Override
    public Boolean visitNor(final Stream<Boolean> negativeDisjoints) {
        return negativeDisjoints.anyMatch(x -> x);
    }

    @Override
    public Boolean visitOr(final Stream<Boolean> disjoints) {
        return disjoints.anyMatch(x -> x);
    }
}
