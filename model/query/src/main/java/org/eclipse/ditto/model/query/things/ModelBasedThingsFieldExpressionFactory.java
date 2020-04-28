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

import static org.eclipse.ditto.model.query.expression.FieldExpressionUtil.FIELD_NAMESPACE;
import static org.eclipse.ditto.model.query.expression.FieldExpressionUtil.FIELD_NAME_THING_ID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.model.things.Thing;

/**
 * {@link ThingsFieldExpressionFactory} which applies a special field mapping
 */
public final class ModelBasedThingsFieldExpressionFactory implements ThingsFieldExpressionFactory {

    private static final Map<String, String> filteringSimpleFieldMappings;

    static {
        final Map<String, String> hashMap = new HashMap<>();
        hashMap.put(FIELD_NAME_THING_ID, FIELD_NAME_THING_ID);
        hashMap.put(FIELD_NAMESPACE, FIELD_NAMESPACE);
        addMapping(hashMap, Thing.JsonFields.POLICY_ID);
        addMapping(hashMap, Thing.JsonFields.REVISION);
        addMapping(hashMap, Thing.JsonFields.MODIFIED);
        addMapping(hashMap, Thing.JsonFields.DEFINITION);
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

    private static void addMapping(final Map<String, String> fieldMappings, final JsonFieldDefinition<?> definition) {
        final JsonPointer pointer = definition.getPointer();
        final String key = pointer.getRoot().map(JsonKey::toString).orElse("");
        final String value = pointer.toString();
        fieldMappings.put(key, value);
    }
}
