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

import java.util.function.Predicate;

import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.model.things.Thing;

/**
 * ExistsFieldExpressionVisitor for Java {@link Predicate}s of {@link Thing}s.
 */
public final class ExistsThingPredicateVisitor implements ExistsFieldExpressionVisitor<Predicate<Thing>> {

    public static Predicate<Thing> apply(final ExistsFieldExpression expression) {
        return expression.acceptExistsVisitor(new ExistsThingPredicateVisitor());
    }

    @Override
    public Predicate<Thing> visitAttribute(final String key) {
        return thing -> thing.getAttributes().map(attributes -> attributes.getValue(key).isPresent())
                .orElse(false);
    }

    @Override
    public Predicate<Thing> visitFeature(final String featureId) {
        return thing -> thing.getFeatures()
                .map(features -> features.getFeature(featureId).isPresent())
                .orElse(false);
    }

    @Override
    public Predicate<Thing> visitFeatureIdProperty(final String featureId, final String property) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .map(feature -> feature.getProperty(property).isPresent())
                .orElse(false);
    }

    @Override
    public Predicate<Thing> visitSimple(final String fieldName) {
        return thing -> thing.toJson().getValue(fieldName).isPresent();
    }
}
