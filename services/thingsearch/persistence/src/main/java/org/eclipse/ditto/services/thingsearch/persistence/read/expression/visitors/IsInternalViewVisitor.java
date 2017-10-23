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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_FEATURE_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;

import java.util.Objects;
import java.util.function.Predicate;

import org.bson.Document;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FieldExpressionVisitor;

/**
 * Computes whether an element of an internal array is related to this field expression.
 */
public class IsInternalViewVisitor implements FieldExpressionVisitor<Predicate<Document>> {

    @Override
    public Predicate<Document> visitAttribute(final String key) {
        return document -> {
            final Object keyInDocument = document.get(FIELD_INTERNAL_KEY);
            final String attributeKeyWithPrefix = FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key;
            return keyInDocument instanceof String &&
                    ((String) keyInDocument).startsWith(attributeKeyWithPrefix);
        };
    }

    @Override
    public Predicate<Document> visitFeature(final String featureId) {
        return document -> {
            final Object featureIdInDocument = document.get(FIELD_INTERNAL_FEATURE_ID);
            return Objects.equals(featureIdInDocument, featureId);
        };
    }

    @Override
    public Predicate<Document> visitFeatureIdProperty(final String featureId, final String property) {
        return document -> {
            final Object keyInDocument = document.get(FIELD_INTERNAL_KEY);
            final String propertyPrefix = FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property;
            final Object featureIdInDocument = document.get(FIELD_INTERNAL_FEATURE_ID);
            return Objects.equals(featureIdInDocument, featureId) &&
                    keyInDocument instanceof String &&
                    ((String) keyInDocument).startsWith(propertyPrefix);

        };
    }

    @Override
    public Predicate<Document> visitFeatureProperty(final String property) {
        return document -> {
            final Object keyInDocument = document.get(FIELD_INTERNAL_KEY);
            final String propertyPrefix = FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property;
            return keyInDocument instanceof String &&
                    ((String) keyInDocument).startsWith(propertyPrefix);
        };
    }

    @Override
    public Predicate<Document> visitSimple(final String fieldName) {
        // the internal array is not relevant here.
        return document -> true;
    }

    @Override
    public Predicate<Document> visitAcl() {
        // the internal array is not relevant here.
        return document -> true;
    }

    @Override
    public Predicate<Document> visitGlobalReads() {
        // the internal array is not relevant here.
        return document -> true;
    }
}
