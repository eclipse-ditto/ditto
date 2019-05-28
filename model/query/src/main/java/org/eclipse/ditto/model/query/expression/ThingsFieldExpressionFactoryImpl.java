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
package org.eclipse.ditto.model.query.expression;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implementation of {@link ThingsFieldExpressionFactory}.
 */
public final class ThingsFieldExpressionFactoryImpl implements ThingsFieldExpressionFactory {

    private static final Map<String, String> mongoSimpleFieldMappings = new HashMap<>();

    static {
        mongoSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        mongoSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
    }

    private final Map<String, String> simpleFieldMappings;

    /**
     * Creates a ThingsFieldExpressionFactory with default field mappings (MongoDB).
     */
    public ThingsFieldExpressionFactoryImpl() {
        this(mongoSimpleFieldMappings);
    }

    /**
     * Creates a ThingsFieldExpressionFactory with custom field mappings.
     *
     * @param simpleFieldMappings the field mappings to apply
     */
    public ThingsFieldExpressionFactoryImpl(final Map<String, String> simpleFieldMappings) {
        this.simpleFieldMappings = simpleFieldMappings;
    }

    @Override
    public FilterFieldExpression filterBy(final String propertyNameWithOptionalLeadingSlash)
            throws IllegalArgumentException {

        requireNonNull(propertyNameWithOptionalLeadingSlash);
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        final Supplier<FilterFieldExpression> defaultSupplier = () -> (FilterFieldExpression) common(propertyName);
        return FieldExpressionUtil.parseFeatureField(requireNonNull(propertyName))
                .<FilterFieldExpression>flatMap(f -> f.getProperty()
                        .flatMap(property ->
                                // we have a feature id and a property path
                                f.getFeatureId().map(id -> new FeatureIdPropertyExpressionImpl(id, property))
                        )
                )
                .orElseGet(defaultSupplier);
    }

    @Override
    public ExistsFieldExpression existsBy(final String propertyNameWithOptionalLeadingSlash) {

        requireNonNull(propertyNameWithOptionalLeadingSlash);
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        return FieldExpressionUtil.parseFeatureField(requireNonNull(propertyName))
                .flatMap(f -> f.getFeatureId()
                        .map(id -> f.getProperty()
                                .<ExistsFieldExpression>map(property ->
                                        new FeatureIdPropertyExpressionImpl(id, property))
                                // we have a feature id but no property path
                                .orElseGet(() -> new FeatureExpressionImpl(id))
                        )
                )
                // we have no feature at all, continue with the other possibilities
                .orElseGet(() -> (ExistsFieldExpression) common(propertyName));
    }

    @Override
    public SortFieldExpression sortBy(final String propertyNameWithOptionalLeadingSlash)
            throws IllegalArgumentException {

        requireNonNull(propertyNameWithOptionalLeadingSlash);
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        return FieldExpressionUtil.parseFeatureField(requireNonNull(propertyName))
                .flatMap(f -> f.getFeatureId()
                        .flatMap(id -> f.getProperty()
                                .flatMap(property -> Optional
                                        .of((SortFieldExpression) new FeatureIdPropertyExpressionImpl(id, property))
                                )
                        )
                )
                .orElseGet(() -> (SortFieldExpression) common(propertyName));
    }

    /**
     * Strip the optional leading slash of the propertyName, because it may be a Json Pointer.
     *
     * @param propertyName the property name which may start with a slash
     * @return the propertyName without leading slash
     */
    private static String stripLeadingSlash(final String propertyName) {
        requireNonNull(propertyName);
        if (propertyName.startsWith("/")) {
            return propertyName.substring(1);
        } else {
            return propertyName;
        }
    }

    /**
     * internal factory method for fields where the filter and the sortOptions field expression is the same.
     *
     * @throws IllegalArgumentException if the property can not be mapped. Because of this, call this method last in the
     * workflow.
     */
    private FieldExpression common(final String propertyName) {
        if (FieldExpressionUtil.isAttributeFieldName(propertyName)) {
            return new AttributeExpressionImpl(FieldExpressionUtil.stripAttributesPrefix(propertyName));
        }

        final String fieldName = simpleFieldMappings.get(propertyName);
        if (fieldName != null) {
            return new SimpleFieldExpressionImpl(fieldName);
        }

        throw new IllegalArgumentException("Unknown property name: " + propertyName);
    }

}
