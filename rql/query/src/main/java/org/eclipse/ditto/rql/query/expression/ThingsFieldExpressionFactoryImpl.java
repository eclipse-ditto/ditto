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

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implementation of {@link ThingsFieldExpressionFactory}.
 */
final class ThingsFieldExpressionFactoryImpl implements ThingsFieldExpressionFactory {

    private final Map<String, String> simpleFieldMappings;

    ThingsFieldExpressionFactoryImpl(final Map<String, String> simpleFieldMappings) {
        this.simpleFieldMappings = Collections.unmodifiableMap(new HashMap<>(simpleFieldMappings));
    }

    @Override
    public FilterFieldExpression filterBy(final String propertyNameWithOptionalLeadingSlash)
            throws IllegalArgumentException {

        requireNonNull(propertyNameWithOptionalLeadingSlash);
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        final Supplier<FilterFieldExpression> defaultSupplier = () -> (FilterFieldExpression) common(propertyName);
        return FieldExpressionUtil.parseFeatureField(requireNonNull(propertyName))
                .<FilterFieldExpression>flatMap(f -> {
                            if (f.isDefinition()) {
                                return f.getFeatureId().map(FeatureDefinitionExpressionImpl::new);
                            } else {
                                return f.getProperty().isPresent()
                                        ? f.getProperty().flatMap(property ->
                                        // we have a feature id and a property path
                                        f.getFeatureId().map(id -> new FeatureIdPropertyExpressionImpl(id, property))
                                )
                                        : f.getDesiredProperty().flatMap(desiredProperty ->
                                        f.getFeatureId()
                                                .map(id -> new FeatureIdDesiredPropertyExpressionImpl(id, desiredProperty))
                                );
                            }
                        }
                )
                .orElseGet(defaultSupplier);
    }

    @Override
    public ExistsFieldExpression existsBy(final String propertyNameWithOptionalLeadingSlash) {
        checkNotNull(propertyNameWithOptionalLeadingSlash, "propertyNameWithOptionalLeadingSlash");
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        return FieldExpressionUtil.parseFeatureField(propertyName)
                .flatMap(f -> f.getFeatureId()
                        .map(id -> f.getProperty().<ExistsFieldExpression>map(
                                        // property
                                        property -> new FeatureIdPropertyExpressionImpl(id, property))
                                // desiredProperty
                                .orElse(f.getDesiredProperty()
                                        .<ExistsFieldExpression>map(
                                                desiredProperty -> new FeatureIdDesiredPropertyExpressionImpl(id,
                                                        desiredProperty))
                                        .orElseGet(() -> {
                                            if (f.isProperties()) {
                                                // we have a feature ID and the properties path,
                                                // but no property
                                                return new FeatureIdPropertiesExpressionImpl(id);
                                            } else if (f.isDesiredProperties()) {
                                                // we have a feature ID and the desired properties path,
                                                // but no desired property
                                                return new FeatureIdDesiredPropertiesExpressionImpl(id);
                                            } else if (f.isDefinition()) {
                                                return new FeatureDefinitionExpressionImpl(id);
                                            } else {
                                                // we have a feature ID but no property path
                                                return new FeatureExpressionImpl(id);
                                            }
                                        }))
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
                        .flatMap(id ->
                                f.getProperty().isPresent()
                                        ? f.getProperty()
                                        .flatMap(property -> Optional.of(
                                                new FeatureIdPropertyExpressionImpl(id, property)))
                                        : f.getDesiredProperty()
                                        .flatMap(desiredProperty -> Optional.of(
                                                new FeatureIdDesiredPropertyExpressionImpl(id, desiredProperty)))
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
     * Internal factory method for fields where the filter and the sortOptions field expression is the same.
     *
     * @throws IllegalArgumentException if the property can not be mapped. Because of this, call this method last in
     * the workflow.
     */
    private FieldExpression common(final String propertyName) {
        if (FieldExpressionUtil.isAttributeFieldName(propertyName)) {
            return new AttributeExpressionImpl(FieldExpressionUtil.stripAttributesPrefix(propertyName));
        }
        if (FieldExpressionUtil.isMetadataFieldName(propertyName)) {
            return new MetadataExpressionImpl(FieldExpressionUtil.stripMetadataPrefix(propertyName));
        }

        final String fieldName = simpleFieldMappings.get(propertyName);
        if (fieldName != null) {
            return new SimpleFieldExpressionImpl(fieldName);
        }

        throw new IllegalArgumentException("Unknown property name: " + propertyName);
    }

}
