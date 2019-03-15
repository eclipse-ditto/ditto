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
package org.eclipse.ditto.model.query.expression;

/**
 * Factory for creating {@link FieldExpression}s for thing search.
 * The only relevant method is {@code filterByNamespace}; all others are only for tests.
 */
public interface ThingsFieldExpressionFactory extends FieldExpressionFactory {

    /**
     * @return a filter expression for the given namespace
     */
    default FilterFieldExpression filterByNamespace() {
        return filterBy(FieldExpressionUtil.FIELD_NAME_NAMESPACE);
    }

    /**
     * Return a exist field expression for the given feature id.
     *
     * @param featureId the feature id
     * @return the exist field expression
     */
    default ExistsFieldExpression existsByFeatureId(final String featureId) {
        return existsBy("features/" + featureId);
    }

    /**
     * Return a filter field expression for the given feature id and property key.
     *
     * @param featureId the feature id
     * @param property the property path
     * @return the filter field expression
     */
    default FilterFieldExpression filterByFeatureProperty(final String featureId, final String property) {
        return filterBy(String.format("features/%s/properties/%s", featureId, property));
    }

    /**
     * Return a exist field expression for the given feature id and property key.
     *
     * @param featureId the feature id
     * @param property the property path
     * @return the exist field expression
     */
    default ExistsFieldExpression existsByFeatureProperty(final String featureId, final String property) {
        return existsBy(String.format("features/%s/properties/%s", featureId, property));
    }

    /**
     * Return a sortOptions field expression for the given feature property key.
     *
     * @param featureId the feature id
     * @param property the property path
     * @return the sortOptions field expression
     */
    default SortFieldExpression sortByFeatureProperty(final String featureId, final String property) {
        return sortBy(String.format("features/%s/properties/%s", featureId, property));
    }

    /**
     * Return a filter field expression for the given attribute key.
     *
     * @param key the key
     * @return the filter field expression
     */
    default FilterFieldExpression filterByAttribute(final String key) {
        return filterBy("attributes/" + key);
    }

    /**
     * Return a exist field expression for the given attribute key.
     *
     * @param key the key
     * @return the exist field expression
     */
    default ExistsFieldExpression existsByAttribute(final String key) {
        return existsBy("attributes/" + key);
    }

    /**
     * Return a sortOptions field expression for the given attribute key.
     *
     * @param key the key
     * @return the sortOptions field expression
     */
    default SortFieldExpression sortByAttribute(final String key) {
        return sortBy("attributes/" + key);
    }

    /**
     * @return a filter expression for the thingId field.
     */
    default FilterFieldExpression filterByThingId() {
        return filterBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
    }

    /**
     * @return a sortOptions expression for the thingId field.
     */
    default SortFieldExpression sortByThingId() {
        return sortBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
    }
}
