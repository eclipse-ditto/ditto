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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;

/**
 * {@link ThingsFieldExpressionFactory} which applies a special field mapping
 */
public final class ModelBasedThingsFieldExpressionFactory implements ThingsFieldExpressionFactory {

    private static final Map<String, String> filteringSimpleFieldMappings = new HashMap<>();

    static {
        filteringSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID,
                FieldExpressionUtil.FIELD_NAME_THING_ID);
        filteringSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
    }

    private final ThingsFieldExpressionFactory delegate;

    public ModelBasedThingsFieldExpressionFactory() {
        this.delegate = new ThingsFieldExpressionFactoryImpl(filteringSimpleFieldMappings);
    }

    @Override
    public ExistsFieldExpression existsByFeatureId(
            final String featureId) {return delegate.existsByFeatureId(featureId);}

    @Override
    public FilterFieldExpression filterByFeatureProperty(
            final String property) {return delegate.filterByFeatureProperty(property);}

    @Override
    public ExistsFieldExpression existsByFeatureProperty(
            final String property) {return delegate.existsByFeatureProperty(property);}

    @Override
    public FilterFieldExpression filterByFeatureProperty(
            final String featureId, final String property) {
        return delegate.filterByFeatureProperty(featureId, property);
    }

    @Override
    public ExistsFieldExpression existsByFeatureProperty(
            final String featureId, final String property) {
        return delegate.existsByFeatureProperty(featureId, property);
    }

    @Override
    public SortFieldExpression sortByFeatureProperty(
            final String featureId, final String property) {return delegate.sortByFeatureProperty(featureId, property);}

    @Override
    public FilterFieldExpression filterByAttribute(
            final String key) {return delegate.filterByAttribute(key);}

    @Override
    public ExistsFieldExpression existsByAttribute(
            final String key) {return delegate.existsByAttribute(key);}

    @Override
    public SortFieldExpression sortByAttribute(
            final String key) {return delegate.sortByAttribute(key);}

    @Override
    public FilterFieldExpression filterByThingId() {return delegate.filterByThingId();}

    @Override
    public SortFieldExpression sortByThingId() {return delegate.sortByThingId();}

    @Override
    public FilterFieldExpression filterByAcl() {return delegate.filterByAcl();}

    @Override
    public FilterFieldExpression filterByGlobalRead() {return delegate.filterByGlobalRead();}

    @Override
    public FilterFieldExpression filterByNamespace() {return delegate.filterByNamespace();}

    @Override
    public FilterFieldExpression filterBy(
            final String propertyName) throws IllegalArgumentException {return delegate.filterBy(propertyName);}

    @Override
    public ExistsFieldExpression existsBy(
            final String propertyName) throws IllegalArgumentException {return delegate.existsBy(propertyName);}

    @Override
    public SortFieldExpression sortBy(
            final String propertyName) throws IllegalArgumentException {return delegate.sortBy(propertyName);}
}
