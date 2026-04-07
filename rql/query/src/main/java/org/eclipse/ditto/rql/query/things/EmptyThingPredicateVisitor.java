/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.Expression;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;

/**
 * ExistsFieldExpressionVisitor for Java {@link Predicate}s of {@link Thing}s which checks whether a field is
 * "empty" — meaning it is absent, {@code null}, an empty array, an empty object or an empty string.
 *
 * @since 3.9.0
 */
public final class EmptyThingPredicateVisitor implements ExistsFieldExpressionVisitor<Predicate<Thing>> {

    private final List<PlaceholderResolver<?>> additionalPlaceholderResolvers;

    private EmptyThingPredicateVisitor() {
        this.additionalPlaceholderResolvers = Collections.emptyList();
    }

    private EmptyThingPredicateVisitor(final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        this.additionalPlaceholderResolvers = Collections.unmodifiableList(
                new ArrayList<>(additionalPlaceholderResolvers));
    }

    /**
     * Applies the passed {@code expression} by visiting with a new created {@link EmptyThingPredicateVisitor}
     * instance not containing any {@code Placeholder}s.
     *
     * @param expression the "empty" expression to visit.
     * @return the Predicate of a thing to test.
     */
    public static Predicate<Thing> apply(final ExistsFieldExpression expression) {
        return expression.acceptExistsVisitor(new EmptyThingPredicateVisitor());
    }

    /**
     * Applies the passed {@code expression} by visiting with a new created {@link EmptyThingPredicateVisitor}
     * instance containing the provided additional {@code PlaceholderResolver}s.
     *
     * @param expression the "empty" expression to visit.
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL "empty" predicates.
     * @return the Predicate of a thing to test.
     */
    public static Predicate<Thing> apply(final ExistsFieldExpression expression,
            final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        return expression.acceptExistsVisitor(new EmptyThingPredicateVisitor(additionalPlaceholderResolvers));
    }

    /**
     * Applies the passed {@code expression} by visiting with a new created {@link EmptyThingPredicateVisitor}
     * instance containing the provided additional {@code PlaceholderResolver}s.
     *
     * @param expression the "empty" expression to visit.
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL "empty" predicates.
     * @return the Predicate of a thing to test.
     */
    public static Predicate<Thing> apply(final ExistsFieldExpression expression,
            final PlaceholderResolver<?>... additionalPlaceholderResolvers) {
        return apply(expression, Arrays.asList(additionalPlaceholderResolvers));
    }

    @Override
    public Predicate<Thing> visitAttribute(final String key) {
        return thing -> thing.getAttributes()
                .map(attributes -> isValueEmpty(attributes.getValueFlatteningArrays(key)))
                .orElse(true); 
    }

    @Override
    public Predicate<Thing> visitFeature(final String featureId) {
        return thing -> thing.getFeatures()
                .map(features -> features.getFeature(featureId)
                        .map(feature -> !feature.getDefinition().isPresent()
                                && !feature.getProperties().isPresent()
                                && !feature.getDesiredProperties().isPresent())
                        .orElse(true))
                .orElse(true); 
    }

    @Override
    public Predicate<Thing> visitFeatureDefinition(final String featureId) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .map(feature -> feature.getDefinition()
                        .map(def -> def.getSize() == 0)
                        .orElse(true))
                .orElse(true); 
    }

    @Override
    public Predicate<Thing> visitFeatureProperties(final CharSequence featureId) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId.toString()))
                .map(feature -> feature.getProperties()
                        .map(props -> props.isEmpty())
                        .orElse(true))
                .orElse(true); 
    }

    @Override
    public Predicate<Thing> visitFeatureDesiredProperties(final CharSequence featureId) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId.toString()))
                .map(feature -> feature.getDesiredProperties()
                        .map(props -> props.isEmpty())
                        .orElse(true))
                .orElse(true); 
    }

    @Override
    public Predicate<Thing> visitFeatureIdProperty(final String featureId, final String property) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .map(properties -> isValueEmpty(properties.getValueFlatteningArrays(property)))
                .orElse(true); 
    }

    @Override
    public Predicate<Thing> visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        return thing -> thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId.toString()))
                .flatMap(Feature::getDesiredProperties)
                .map(properties -> isValueEmpty(properties.getValueFlatteningArrays(property)))
                .orElse(true);
    }

    @Override
    public Predicate<Thing> visitSimple(final String fieldName) {
        return thing -> {
            final Optional<JsonValue> value = thing.toJson().getValue(fieldName);
            if (value.isPresent()) {
                return isJsonValueEmpty(value.get());
            }
            // field not found in thing JSON, check placeholder resolvers
            return isEmptyInAdditionalPlaceholderResolvers(fieldName);
        };
    }

    @Override
    public Predicate<Thing> visitMetadata(final String key) {
        return thing -> thing.getMetadata()
                .map(metadata -> isValueEmpty(metadata.getValue(key)))
                .orElse(true); 
    }

    /**
     * Checks whether a field value is considered "empty".
     * Empty means: absent (empty Optional), null, empty array, empty object, or empty string.
     */
    private static boolean isValueEmpty(final Optional<JsonValue> optionalValue) {
        if (!optionalValue.isPresent()) {
            return true; // absent
        }
        return isJsonValueEmpty(optionalValue.get());
    }

    private static boolean isJsonValueEmpty(final JsonValue value) {
        if (value.isNull()) {
            return true;
        }
        if (value.isString()) {
            return value.asString().isEmpty();
        }
        if (value.isArray()) {
            return value.asArray().isEmpty();
        }
        if (value.isObject()) {
            return value.asObject().isEmpty();
        }
        return false;
    }

    /**
     * Checks whether the given field name is "empty" according to placeholder resolvers.
     * Returns {@code true} (empty) when:
     * <ul>
     *   <li>No placeholder resolver can resolve the field</li>
     *   <li>The resolved values are empty</li>
     *   <li>All resolved values are empty strings</li>
     * </ul>
     */
    private boolean isEmptyInAdditionalPlaceholderResolvers(final String fieldName) {
        final String[] fieldNameSplit = fieldName.split(Expression.SEPARATOR, 2);
        if (fieldNameSplit.length > 1) {
            final String placeholderPrefix = fieldNameSplit[0];
            final String placeholderName = fieldNameSplit[1];
            return additionalPlaceholderResolvers.stream()
                    .filter(pr -> placeholderPrefix.equals(pr.getPrefix()))
                    .filter(pr -> pr.supports(placeholderName))
                    .map(pr -> pr.resolveValues(placeholderName))
                    .findFirst()
                    .map(resolvedValues -> resolvedValues.isEmpty() ||
                            resolvedValues.stream().allMatch(String::isEmpty))
                    .orElse(true);
        } else {
            return true;
        }
    }

}
