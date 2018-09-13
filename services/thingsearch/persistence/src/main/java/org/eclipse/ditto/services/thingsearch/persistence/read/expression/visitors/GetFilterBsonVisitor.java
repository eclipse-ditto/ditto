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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GLOBAL_READS;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_FEATURE_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_VALUE;

import java.util.function.Function;

import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.visitors.FilterFieldExpressionVisitor;

import com.mongodb.client.model.Filters;

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
        final String attributeKeyWithPrefix = FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key;
        final Bson keyRestrictionBson = Filters.eq(FIELD_INTERNAL_KEY, attributeKeyWithPrefix);

        // match 'null' on 'f' field to be able to use the index
        final Bson nullFeatureId = new BsonDocument().append(FIELD_INTERNAL_FEATURE_ID, BsonNull.VALUE);

        return Filters.elemMatch(FIELD_INTERNAL,
                Filters.and(keyRestrictionBson, nullFeatureId, predicateFunction.apply(FIELD_INTERNAL_VALUE)));
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return Filters.elemMatch(
                FIELD_INTERNAL,
                Filters.and(
                        Filters.eq(FIELD_INTERNAL_KEY, FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property),
                        Filters.eq(FIELD_INTERNAL_FEATURE_ID, featureId),
                        predicateFunction.apply(FIELD_INTERNAL_VALUE)));
    }

    @Override
    public Bson visitFeatureProperty(final String property) {
        return Filters.elemMatch(FIELD_INTERNAL, Filters.and(
                Filters.eq(FIELD_INTERNAL_KEY, FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property),
                predicateFunction.apply(FIELD_INTERNAL_VALUE)));
    }

    @Override
    public Bson visitSimple(final String fieldName) {
        return predicateFunction.apply(fieldName);
    }

    @Override
    public Bson visitAcl() {
        return predicateFunction.apply(FIELD_INTERNAL + "." + FIELD_ACL);
    }

    @Override
    public Bson visitGlobalReads() {
        return predicateFunction.apply(
                FIELD_INTERNAL + "." + FIELD_GLOBAL_READS);
    }
}
