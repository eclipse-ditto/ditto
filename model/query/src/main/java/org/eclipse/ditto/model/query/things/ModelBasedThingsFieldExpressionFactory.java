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

import static org.eclipse.ditto.model.query.expression.FieldExpressionUtil.FIELD_NAMESPACE;
import static org.eclipse.ditto.model.query.expression.FieldExpressionUtil.FIELD_NAME_THING_ID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;

/**
 * {@link ThingsFieldExpressionFactory} which applies a special field mapping
 */
public final class ModelBasedThingsFieldExpressionFactory implements ThingsFieldExpressionFactory {

    private static final Map<String, String> filteringSimpleFieldMappings;

    static {
        final Map<String, String> hashMap = new HashMap<>();
        hashMap.put(FIELD_NAME_THING_ID, FIELD_NAME_THING_ID);
        hashMap.put(FIELD_NAMESPACE, FIELD_NAMESPACE);
        filteringSimpleFieldMappings = Collections.unmodifiableMap(hashMap);
    }

    private final ThingsFieldExpressionFactory delegate;

    public ModelBasedThingsFieldExpressionFactory() {
        this.delegate = new ThingsFieldExpressionFactoryImpl(filteringSimpleFieldMappings);
    }

    @Override
    public FilterFieldExpression filterBy(final String propertyName) {
        return delegate.filterBy(propertyName);
    }

    @Override
    public ExistsFieldExpression existsBy(final String propertyName) {
        return delegate.existsBy(propertyName);
    }

    @Override
    public SortFieldExpression sortBy(final String propertyName) {
        return delegate.sortBy(propertyName);
    }
}
