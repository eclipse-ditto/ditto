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
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES_PATH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURES_PATH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_VALUE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.PROPERTIES;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.visitors.FilterFieldExpressionVisitor;

import com.mongodb.client.model.Filters;

/**
 * Creates a Mongo Bson object for field-based search criteria.
 */
public class GetFilterBsonVisitor extends AbstractFieldBsonCreator implements FilterFieldExpressionVisitor<Bson> {

    private final Function<String, Bson> predicateFunction;
    private final Bson valueFilter;

    /**
     * Creates a visitor object to create a Mongo Bson object for field-based search criteria.
     *
     * @param predicateFunction the function for creating the predicate-part (e.g. "eq", "ne", ...) of the criteria
     */
    private GetFilterBsonVisitor(final Function<String, Bson> predicateFunction,
            @Nullable List<String> authorizationSubjectIds) {

        super(authorizationSubjectIds);
        this.predicateFunction = predicateFunction;
        this.valueFilter = predicateFunction.apply(FIELD_INTERNAL_VALUE);
    }

    /**
     * Creates a Mongo Bson object for field-based search criteria.
     *
     * @param expression the expression to create a filter for.
     * @param predicateFunction the function for creating the predicate-part (e.g. "eq", "ne", ...) of the criteria
     * @param authorizationSubjectIds subject IDs to check for authorization, or null to not restrict visibility at all
     * @return the complete Bson for the field-based search criteria
     */
    public static Bson apply(final FilterFieldExpression expression,
            final Function<String, Bson> predicateFunction,
            @Nullable final List<String> authorizationSubjectIds) {

        return expression.acceptFilterVisitor(new GetFilterBsonVisitor(predicateFunction, authorizationSubjectIds));
    }

    /**
     * Create Bson without considering authorization.
     *
     * @param expression the field expression.
     * @param predicateFunction the predicate creator.
     * @return the filter Bson with no restriction on visibility.
     */
    public static Bson sudoApply(final FilterFieldExpression expression,
            final Function<String, Bson> predicateFunction) {
        return expression.acceptFilterVisitor(new GetFilterBsonVisitor(predicateFunction, null));
    }

    @Override
    public Bson visitAttribute(final String key) {
        return matchKeyValue(FIELD_ATTRIBUTES_PATH + key);
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return matchKeyValue(FIELD_FEATURES_PATH + featureId + PROPERTIES + property);
    }


    @Override
    Bson visitPointer(final String pointer) {
        return matchKeyValue(pointer);
    }

    @Override
    Bson visitRootLevelField(final String fieldName) {
        return predicateFunction.apply(fieldName);
    }

    private Bson matchKeyValue(final String key) {
        final Bson keyValueFilter = Filters.and(Filters.eq(FIELD_INTERNAL_KEY, key), valueFilter);
        return Filters.elemMatch(FIELD_INTERNAL,
                getAuthorizationBson()
                        .map(authBson -> Filters.and(keyValueFilter, authBson))
                        .orElse(keyValueFilter));
    }
}
