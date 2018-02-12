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
package org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors;

import java.util.stream.Stream;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.PolicyRestrictedFieldExpression;

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
