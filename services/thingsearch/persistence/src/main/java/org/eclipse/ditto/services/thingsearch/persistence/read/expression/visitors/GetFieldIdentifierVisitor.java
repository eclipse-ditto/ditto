/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FieldExpressionVisitor;

/**
 * Gets a unique identifier of this field in a Thing.
 */
public class GetFieldIdentifierVisitor implements FieldExpressionVisitor<String> {

    private GetFieldIdentifierVisitor() {
        // only internally instantiable
    }

    /**
     * Gets a unique identifier of this field in a Thing.
     *
     * @param expression the expression denoting a field.
     * @return The unique identifier.
     */
    public static String apply(final FieldExpression expression) {
        return expression.accept(new GetFieldIdentifierVisitor());
    }

    @Override
    public String visitAttribute(final String key) {
        return PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key;
    }

    @Override
    public String visitFeature(final String featureId) {
        return PersistenceConstants.FIELD_FEATURES_PREFIX + featureId;
    }

    @Override
    public String visitFeatureIdProperty(final String featureId, final String property) {
        return PersistenceConstants.FIELD_FEATURES_PREFIX + featureId + PersistenceConstants.SLASH +
                PersistenceConstants.FIELD_PROPERTIES + property;
    }

    @Override
    public String visitFeatureProperty(final String property) {
        return PersistenceConstants.FIELD_FEATURES + "/*/" + PersistenceConstants.FIELD_PROPERTIES +
                PersistenceConstants.SLASH + property;
    }

    @Override
    public String visitSimple(final String fieldName) {
        return fieldName;
    }

    @Override
    public String visitAcl() {
        return PersistenceConstants.FIELD_ACL;
    }

    @Override
    public String visitGlobalReads() {
        return PersistenceConstants.FIELD_GLOBAL_READS;
    }
}
