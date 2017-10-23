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

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionUtil;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.ExistsFieldExpressionVisitor;

/**
 * Creates a Mongo Bson object for field-based exists criteria.
 */
public class GetExistsBsonVisitor implements ExistsFieldExpressionVisitor<Bson> {

    /**
     * Creates a Mongo Bson object for field-based exists criteria.
     *
     * @param expression the expression of the resource whose existence is under scrutiny.
     * @return the complete Bson for the field-based exists criteria
     */
    public static Bson apply(final ExistsFieldExpression expression) {
        return expression.acceptExistsVisitor(new GetExistsBsonVisitor());
    }

    @Override
    public Bson visitAttribute(final String key) {
        return Filters.regex(PersistenceConstants.FIELD_PATH_KEY, FieldExpressionUtil.wrapExistsRegex(
                PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key));
    }

    @Override
    public Bson visitFeature(final String featureId) {
        return Filters.eq(PersistenceConstants.FIELD_INTERNAL + PersistenceConstants.DOT +
                PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId);
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL,
                Filters.and(Filters.regex(PersistenceConstants.FIELD_INTERNAL_KEY,
                FieldExpressionUtil.wrapExistsRegex(
                        PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property)),
                Filters.eq(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId)));
    }

    @Override
    public Bson visitFeatureProperty(final String property) {
        return Filters.regex(PersistenceConstants.FIELD_PATH_KEY,
                FieldExpressionUtil.wrapExistsRegex(
                        PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property));
    }

    @Override
    public Bson visitSimple(final String fieldName) {
        return Filters.exists(fieldName);
    }
}
