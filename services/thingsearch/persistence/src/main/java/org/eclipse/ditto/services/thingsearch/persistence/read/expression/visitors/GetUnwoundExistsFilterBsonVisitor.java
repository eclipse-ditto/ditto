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
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.model.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

import com.mongodb.client.model.Filters;

/**
 * Creates a Mongo Bson object for field-based search criteria on the *unwound* __internal array documents.
 */
public class GetUnwoundExistsFilterBsonVisitor implements ExistsFieldExpressionVisitor<Bson> {

    private GetUnwoundExistsFilterBsonVisitor() {
        // only internally instantiable
    }

    /**
     * Creates a Mongo Bson object for field-based search criteria on the *unwound* __internal array documents.
     *
     * @param expression the expression for the resource whose existence is under scrutiny.
     * @return the complete Bson for the field-based search criteria
     */
    public static Bson apply(final ExistsFieldExpression expression) {
        return expression.acceptExistsVisitor(new GetUnwoundExistsFilterBsonVisitor());
    }

    @Override
    public Bson visitAttribute(final String key) {
        return new GetExistsBsonVisitor().visitAttribute(key);
    }

    @Override
    public Bson visitFeature(final String featureId) {
        return new GetExistsBsonVisitor().visitFeature(featureId);
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return Filters.and(Filters.regex(PersistenceConstants.FIELD_PATH_KEY, FieldExpressionUtil.wrapExistsRegex(
                PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property)),
                Filters.eq(PersistenceConstants.FIELD_FEATURE_PATH_KEY, featureId));
    }

    @Override
    public Bson visitFeatureProperty(final String property) {
        return new GetExistsBsonVisitor().visitFeatureProperty(property);
    }

    @Override
    public Bson visitSimple(final String fieldName) {
        return new GetExistsBsonVisitor().visitSimple(fieldName);
    }
}
