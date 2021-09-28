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
package org.eclipse.ditto.rql.query.expression;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.placeholders.Expression;
import org.eclipse.ditto.placeholders.Placeholder;

/**
 * Factory for creating {@link FieldExpression}s for thing search.
 * The only relevant method is {@code filterByNamespace}; all others are only for tests.
 */
public interface ThingsFieldExpressionFactory extends FieldExpressionFactory {

    /**
     * Creates a ThingsFieldExpressionFactory with custom field mappings.
     *
     * @param simpleFieldMappings the field mappings to apply.
     * @return the created ThingsFieldExpressionFactory
     * @since 2.0.0
     */
    static ThingsFieldExpressionFactory of(final Map<String, String> simpleFieldMappings) {
        return new ThingsFieldExpressionFactoryImpl(simpleFieldMappings);
    }

    /**
     * Creates a ThingsFieldExpressionFactory with custom field mappings and additional {@code placeholders} to also
     * accept when validating if an RQL query contains supported fields.
     *
     * @param simpleFieldMappings the field mappings to apply.
     * @param placeholders the {@code Placeholder}s to also respect for supported fields in the RQL
     * @return the created ThingsFieldExpressionFactory
     * @since 2.2.0
     */
    static ThingsFieldExpressionFactory of(final Map<String, String> simpleFieldMappings,
            final Collection<Placeholder<?>> placeholders) {
        final Map<String, String> combinedFieldMappings = new HashMap<>(simpleFieldMappings);
        placeholders.forEach(placeholder ->
                placeholder.getSupportedNames()
                        .stream()
                        .map(name -> placeholder.getPrefix() + Expression.SEPARATOR + name)
                        .forEach(combinedPlaceholder ->
                                combinedFieldMappings.put(combinedPlaceholder, combinedPlaceholder))
        );
        return new ThingsFieldExpressionFactoryImpl(combinedFieldMappings);
    }

    /**
     * @return a filter expression for the given namespace
     */
    default FilterFieldExpression filterByNamespace() {
        return filterBy(FieldExpressionUtil.FIELD_NAME_NAMESPACE);
    }

    /**
     * Return an "exist" field expression for the given feature ID.
     *
     * @param featureId the feature ID.
     * @return the "exists" field expression.
     */
    default ExistsFieldExpression existsByFeatureId(final String featureId) {
        return existsBy("features/" + featureId);
    }

    /**
     * Return a filter field expression for the given feature ID and property key.
     *
     * @param featureId the feature ID.
     * @param property the property path.
     * @return the filter field expression.
     */
    default FilterFieldExpression filterByFeatureProperty(final String featureId, final String property) {
        return filterBy(String.format("features/%s/properties/%s", featureId, property));
    }

    /**
     * Return an "exists" field expression for the given feature ID and its properties.
     *
     * @param featureId the feature ID.
     * @return the "exists" field expression.
     */
    default ExistsFieldExpression existsByFeatureProperties(final CharSequence featureId) {
        return existsBy(String.format("features/%s/properties", featureId));
    }

    /**
     * Return an "exists" field expression for the given feature ID and property key.
     *
     * @param featureId the feature ID.
     * @param property the property path.
     * @return the "exists" field expression.
     */
    default ExistsFieldExpression existsByFeatureProperty(final String featureId, final String property) {
        return existsBy(String.format("features/%s/properties/%s", featureId, property));
    }

    /**
     * Return a "sortOptions" field expression for the given feature desired property key.
     *
     * @param featureId the feature ID.
     * @param property the desired property path.
     * @return the "sortOptions" field expression.
     */
    default SortFieldExpression sortByFeatureProperty(final String featureId, final String property) {
        return sortBy(String.format("features/%s/properties/%s", featureId, property));
    }

    /**
     * Return a filter field expression for the given feature ID and desired property key.
     *
     * @param featureId the feature ID.
     * @param desiredProperty the desired property path.
     * @return the filter field expression.
     */
    default FilterFieldExpression filterByFeatureDesiredProperty(final CharSequence featureId,
            final CharSequence desiredProperty) {
        return filterBy(String.format("features/%s/desiredProperties/%s", featureId, desiredProperty));
    }

    /**
     * Return an "exists" field expression for the given feature ID and its desired properties.
     *
     * @param featureId the feature ID.
     * @return the "exists" field expression.
     */
    default ExistsFieldExpression existsByFeatureDesiredProperties(final CharSequence featureId) {
        return existsBy(String.format("features/%s/desiredProperties", featureId));
    }

    /**
     * Return an "exists" field expression for the given feature ID and desired property key.
     *
     * @param featureId the feature ID.
     * @param desiredProperty the desired property path.
     * @return the "exists" field expression.
     */
    default ExistsFieldExpression existsByFeatureDesiredProperty(final CharSequence featureId,
            final CharSequence desiredProperty) {
        return existsBy(String.format("features/%s/desiredProperties/%s", featureId, desiredProperty));
    }

    /**
     * Return a "sortOptions" field expression for the given feature desired property key.
     *
     * @param featureId the feature ID.
     * @param desiredProperty the desired property path.
     * @return the "sortOptions" field expression.
     */
    default SortFieldExpression sortByFeatureDesiredProperty(final CharSequence featureId,
            final CharSequence desiredProperty) {
        return sortBy(String.format("features/%s/desiredProperties/%s", featureId, desiredProperty));
    }

    /**
     * Return a filter field expression for the given attribute key.
     *
     * @param key the key.
     * @return the filter field expression.
     */
    default FilterFieldExpression filterByAttribute(final String key) {
        return filterBy("attributes/" + key);
    }

    /**
     * Return an "exists" field expression for the given attribute key.
     *
     * @param key the key.
     * @return the "exists" field expression.
     */
    default ExistsFieldExpression existsByAttribute(final String key) {
        return existsBy("attributes/" + key);
    }

    /**
     * Return a "sortOptions" field expression for the given attribute key.
     *
     * @param key the key.
     * @return the "sortOptions" field expression.
     */
    default SortFieldExpression sortByAttribute(final String key) {
        return sortBy("attributes/" + key);
    }

    /**
     * @return a filter expression for the "thingId" field.
     */
    default FilterFieldExpression filterByThingId() {
        return filterBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
    }

    /**
     * @return a filter expression for the "definition" field.
     * @since 2.2.0
     */
    default FilterFieldExpression filterByDefinition() {
        return filterBy(FieldExpressionUtil.FIELD_NAME_DEFINITION);
    }

    /**
     * @return a sortOptions expression for the "thingId" field.
     */
    default SortFieldExpression sortByThingId() {
        return sortBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
    }

    /**
     * @return a sortOptions expression for the "definition" field.
     * @since 2.2.0
     */
    default SortFieldExpression sortByDefinition() {
        return sortBy(FieldExpressionUtil.FIELD_NAME_DEFINITION);
    }

}
