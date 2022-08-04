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
package org.eclipse.ditto.rql.query.things;

import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.FilterFieldExpressionVisitor;
import org.eclipse.ditto.things.model.Thing;

/**
 * FilterFieldExpressionVisitor for Java {@link Predicate}s of {@link Thing}s.
 */
public final class FilterThingPredicateVisitor implements FilterFieldExpressionVisitor<Predicate<Thing>> {

    private final Function<String, Predicate<Thing>> predicateFunction;

    private FilterThingPredicateVisitor(final Function<String, Predicate<Thing>> predicateFunction) {
        this.predicateFunction = predicateFunction;
    }

    public static Predicate<Thing> apply(final FilterFieldExpression expression,
            final Function<String, Predicate<Thing>> predicateFunction) {

        return expression.acceptFilterVisitor(new FilterThingPredicateVisitor(predicateFunction));
    }

    @Override
    public Predicate<Thing> visitAttribute(final String key) {
        return predicateFunction.apply("/attributes/" + key);
    }

    @Override
    public Predicate<Thing> visitFeatureDefinition(final String featureId) {
        return predicateFunction.apply("/features/" + featureId + "/definition");
    }

    @Override
    public Predicate<Thing> visitFeatureIdProperty(final String featureId, final String property) {
        return predicateFunction.apply("/features/" + featureId + "/properties/" + property);
    }

    @Override
    public Predicate<Thing> visitFeatureIdDesiredProperty(final CharSequence featureId,
            final CharSequence desiredProperty) {

        return predicateFunction.apply("/features/" + featureId + "/desiredProperties/" + desiredProperty);
    }

    @Override
    public Predicate<Thing> visitSimple(final String fieldName) {
        return predicateFunction.apply(fieldName);
    }

    @Override
    public Predicate<Thing> visitMetadata(final String key) {
        return predicateFunction.apply("_metadata/" + key);
    }

}
