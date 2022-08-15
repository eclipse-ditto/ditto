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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.ditto.placeholders.Expression;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.things.model.Thing;

/**
 * ExistsFieldExpressionVisitor for Java {@link Predicate}s of {@link Thing}s and additionally added
 * {@code PlaceholderResolver}s.
 */
public final class ExistsThingPredicateVisitor implements ExistsFieldExpressionVisitor<Predicate<Thing>> {

    private final List<PlaceholderResolver<?>> additionalPlaceholderResolvers;

    public ExistsThingPredicateVisitor() {
        this.additionalPlaceholderResolvers = Collections.emptyList();
    }

    private ExistsThingPredicateVisitor(final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        this.additionalPlaceholderResolvers = Collections.unmodifiableList(
                new ArrayList<>(additionalPlaceholderResolvers));
    }

    /**
     * Applies the passed {@code expression} by visiting with a new created {@link ExistsThingPredicateVisitor} instance
     * not containing any {@code Placeholder}s.
     *
     * @param expression the "exists" expression to visit.
     * @return the Predicate of a thing to test.
     */
    public static Predicate<Thing> apply(final ExistsFieldExpression expression) {
        return expression.acceptExistsVisitor(new ExistsThingPredicateVisitor());
    }

    /**
     * Applies the passed {@code expression} by visiting with a new created {@link ExistsThingPredicateVisitor} instance
     * containing the provided additional {@code PlaceholderResolver}s.
     *
     * @param expression the "exists" expression to visit.
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL "exists" predicates.
     * @return the Predicate of a thing to test.
     * @since 2.2.0
     */
    public static Predicate<Thing> apply(final ExistsFieldExpression expression,
            final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        return expression.acceptExistsVisitor(new ExistsThingPredicateVisitor(additionalPlaceholderResolvers));
    }

    /**
     * Applies the passed {@code expression} by visiting with a new created {@link ExistsThingPredicateVisitor} instance
     * containing the provided additional {@code PlaceholderResolver}s.
     *
     * @param expression the "exists" expression to visit.
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL "exists" predicates.
     * @return the Predicate of a thing to test.
     * @since 2.2.0
     */
    public static Predicate<Thing> apply(final ExistsFieldExpression expression,
            final PlaceholderResolver<?>... additionalPlaceholderResolvers) {
        return apply(expression, Arrays.asList(additionalPlaceholderResolvers));
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
    public Predicate<Thing> visitFeatureDefinition(final String featureId) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .map(feature -> feature.getDefinition().isPresent())
                .orElse(false);
    }

    @Override
    public Predicate<Thing> visitFeatureProperties(final CharSequence featureId) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId.toString()))
                .map(feature -> feature.getProperties().isPresent())
                .orElse(false);
    }

    @Override
    public Predicate<Thing> visitFeatureDesiredProperties(final CharSequence featureId) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId.toString()))
                .map(feature -> feature.getDesiredProperties().isPresent())
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
    public Predicate<Thing> visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId.toString()))
                .map(feature -> feature.getDesiredProperty(property).isPresent())
                .orElse(false);
    }

    @Override
    public Predicate<Thing> visitSimple(final String fieldName) {
        return thing -> thing.toJson().getValue(fieldName).isPresent() ||
                existsInAdditionalPlaceholderResolvers(fieldName);
    }

    private boolean existsInAdditionalPlaceholderResolvers(final String fieldName) {
        final String[] fieldNameSplit = fieldName.split(Expression.SEPARATOR, 2);
        if (fieldNameSplit.length > 1) {
            final String placeholderPrefix = fieldNameSplit[0];
            final String placeholderName = fieldNameSplit[1];
            return additionalPlaceholderResolvers.stream()
                    .filter(pr -> placeholderPrefix.equals(pr.getPrefix()))
                    .filter(pr -> pr.supports(placeholderName))
                    .map(pr -> pr.resolveValues(placeholderName))
                    .anyMatch(resolvedValues -> !resolvedValues.isEmpty());
        } else {
            return false;
        }
    }

    @Override
    public Predicate<Thing> visitMetadata(final String key) {
        return thing -> thing.getMetadata()
                .map(metadata -> metadata.contains(key))
                .orElse(false);
    }

}
