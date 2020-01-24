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
package org.eclipse.ditto.model.query.things;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
    public Predicate<Thing> visitAnd(final List<Predicate<Thing>> conjuncts) {
        return thing -> conjuncts.stream().allMatch(p -> p.test(thing));
    }

    @Override
    public Predicate<Thing> visitAnd(final Stream<Predicate<Thing>> conjuncts) {
        return visitAnd(conjuncts.collect(Collectors.toList()));
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
    public Predicate<Thing> visitNor(final List<Predicate<Thing>> negativeDisjoints) {
        return thing -> negativeDisjoints.stream().noneMatch(p -> p.test(thing));
    }

    @Override
    public Predicate<Thing> visitNor(final Stream<Predicate<Thing>> negativeDisjoints) {
        return visitNor(negativeDisjoints.collect(Collectors.toList()));
    }

    @Override
    public Predicate<Thing> visitOr(final List<Predicate<Thing>> disjoints) {
        return thing -> disjoints.stream().anyMatch(p -> p.test(thing));
    }

    @Override
    public Predicate<Thing> visitOr(final Stream<Predicate<Thing>> disjoints) {
        return visitOr(disjoints.collect(Collectors.toList()));
    }
}
