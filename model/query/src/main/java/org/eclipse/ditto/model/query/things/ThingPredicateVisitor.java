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
package org.eclipse.ditto.model.query.things;

import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.things.Thing;

/**
 * CriteriaVisitor for Java {@link Predicate}s of {@link Thing}s.
 */
public final class ThingPredicateVisitor implements CriteriaVisitor<Predicate<Thing>> {

    private ThingPredicateVisitor() {
        // only internally instantiable
    }

    public static Predicate<Thing> apply(final Criteria criteria) {
        return criteria.accept(new ThingPredicateVisitor());
    }

    @Override
    public Predicate<Thing> visitAnd(final Stream<Predicate<Thing>> conjuncts) {
        return thing -> conjuncts.allMatch(p -> p.test(thing));
    }

    @Override
    public Predicate<Thing> visitAny() {
        return any -> true;
    }

    @Override
    public Predicate<Thing> visitExists(final ExistsFieldExpression fieldExpression) {
        return ExistsThingPredicateVisitor.apply(fieldExpression);
    }

    @Override
    public Predicate<Thing> visitField(final FilterFieldExpression fieldExpression,
            final org.eclipse.ditto.model.query.criteria.Predicate predicate) {
        return FilterThingPredicateVisitor.apply(fieldExpression,
                predicate.accept(ThingPredicatePredicateVisitor.getInstance()));
    }

    @Override
    public Predicate<Thing> visitNor(final Stream<Predicate<Thing>> negativeDisjoints) {
        return thing -> negativeDisjoints.noneMatch(p -> p.test(thing));
    }

    @Override
    public Predicate<Thing> visitOr(final Stream<Predicate<Thing>> disjoints) {
        return thing -> disjoints.anyMatch(p -> p.test(thing));
    }
}
