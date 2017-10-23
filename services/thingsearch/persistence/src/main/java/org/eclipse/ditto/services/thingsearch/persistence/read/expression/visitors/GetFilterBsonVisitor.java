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

import java.util.function.Function;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FilterFieldExpressionVisitor;

/**
 * Creates a Mongo Bson object for field-based search criteria.
 */
public class GetFilterBsonVisitor implements FilterFieldExpressionVisitor<Bson> {

    private final Function<String, Bson> predicateFunction;

    /**
     * Creates a visitor object to create a Mongo Bson object for field-based search criteria.
     *
     * @param predicateFunction the function for creating the predicate-part (e.g. "eq", "ne", ...) of the criteria
     */
    private GetFilterBsonVisitor(final Function<String, Bson> predicateFunction) {
        this.predicateFunction = predicateFunction;
    }

    /**
     * Creates a Mongo Bson object for field-based search criteria.
     *
     * @param expression the expression to create a filter for.
     * @param predicateFunction the function for creating the predicate-part (e.g. "eq", "ne", ...) of the criteria
     * @return the complete Bson for the field-based search criteria
     */
    public static Bson apply(final FilterFieldExpression expression, final Function<String, Bson> predicateFunction) {
        return expression.acceptFilterVisitor(new GetFilterBsonVisitor(predicateFunction));
    }

    @Override
    public Bson visitAttribute(final String key) {
        final String attributeKeyWithPrefix = PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key;
        final Bson keyRestrictionBson = Filters.eq(PersistenceConstants.FIELD_INTERNAL_KEY, attributeKeyWithPrefix);

        return Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL,
                Filters.and(keyRestrictionBson, predicateFunction.apply(PersistenceConstants.FIELD_INTERNAL_VALUE)));
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return Filters.elemMatch(
                PersistenceConstants.FIELD_INTERNAL, Filters.and(Filters.eq(PersistenceConstants.FIELD_INTERNAL_KEY,
                        PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property),
                Filters.eq(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId),
                predicateFunction.apply(PersistenceConstants.FIELD_INTERNAL_VALUE)));
    }

    @Override
    public Bson visitFeatureProperty(final String property) {
        return Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL, Filters.and(
                Filters.eq(
                        PersistenceConstants.FIELD_INTERNAL_KEY,
                        PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property),
                predicateFunction.apply(PersistenceConstants.FIELD_INTERNAL_VALUE)));
    }

    @Override
    public Bson visitSimple(final String fieldName) {
        return predicateFunction.apply(fieldName);
    }

    @Override
    public Bson visitAcl() {
        return predicateFunction.apply(PersistenceConstants.FIELD_INTERNAL + "." + PersistenceConstants.FIELD_ACL);
    }

    @Override
    public Bson visitGlobalReads() {
        return predicateFunction.apply(
                PersistenceConstants.FIELD_INTERNAL + "." + PersistenceConstants.FIELD_GLOBAL_READS);
    }
}
