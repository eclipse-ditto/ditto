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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.CreateUnwoundBsonFieldVisitor;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;

/**
 * Creates the Bson object to filter out irrelevant fields.
 */
public class CreateUnwoundBsonVisitor implements CriteriaVisitor<Function<Boolean, Optional<Bson>>> {

    private static CreateUnwoundBsonVisitor instance;

    private CreateUnwoundBsonVisitor() {
        // only internally instantiable
    }

    /**
     * Gets the singleton instance of this {@link CreateUnwoundBsonVisitor}.
     *
     * @return the singleton instance.
     */
    public static CreateUnwoundBsonVisitor getInstance() {
        if (null == instance) {
            instance = new CreateUnwoundBsonVisitor();
        }
        return instance;
    }

    /**
     * Creates unwound Bson, starting in the positive state.
     *
     * @param criteria The criteria to create unwound Bson for.
     * @return The generated Bson.
     */
    public static Optional<Bson> apply(final Criteria criteria) {
        return criteria.accept(getInstance()).apply(true);
    }

    @Override
    public Function<Boolean, Optional<Bson>> visitAnd(final Stream<Function<Boolean, Optional<Bson>>> conjuncts) {
        return buildCompositeBsonCreator(conjuncts, Function.identity(), Filters::or, Filters::and);
    }

    @Override
    public Function<Boolean, Optional<Bson>> visitAny() {
        return isPositive -> Optional.empty();
    }

    @Override
    public Function<Boolean, Optional<Bson>> visitExists(final ExistsFieldExpression fieldExpression) {
        return isPositive -> fieldExpression.accept(new CreateUnwoundBsonFieldVisitor(null));
    }

    @Override
    public Function<Boolean, Optional<Bson>> visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return isPositive -> fieldExpression.accept(new CreateUnwoundBsonFieldVisitor(predicate));
    }

    @Override
    public Function<Boolean, Optional<Bson>> visitNor(final Stream<Function<Boolean, Optional<Bson>>> negativeDisjoints) {
        return buildCompositeBsonCreator(negativeDisjoints, isPostive -> !isPostive,
                children -> Filters.nor(Filters.and(children)),
                children -> Filters.nor(Filters.or(children)));
    }

    @Override
    public Function<Boolean, Optional<Bson>> visitOr(final Stream<Function<Boolean, Optional<Bson>>> disjoints) {
        return buildCompositeBsonCreator(disjoints, Function.identity(), Filters::or, Filters::and);
    }

    // common template of And, Or, Nor
    private Function<Boolean, Optional<Bson>> buildCompositeBsonCreator(
            final Stream<Function<Boolean, Optional<Bson>>> childFunctions,
            final Function<Boolean, Boolean> areChildrenPositive,
            final Function<Iterable<Bson>, Bson> positiveFilter, final Function<Iterable<Bson>, Bson> negativeFilter) {

        return isPositive -> {

            final Boolean childPositivity = areChildrenPositive.apply(isPositive);
            final List<Bson> childBsons = childFunctions
                    .flatMap(childFunction -> childFunction.apply(childPositivity)
                            .map(Stream::of)
                            .orElse(Stream.empty()))
                    .collect(Collectors.toList());

            if (childBsons.isEmpty()) {
                return Optional.empty();
            } else {
                final Function<Iterable<Bson>, Bson> filter = isPositive ? positiveFilter : negativeFilter;
                return Optional.of(filter.apply(childBsons));
            }
        };
    }
}
