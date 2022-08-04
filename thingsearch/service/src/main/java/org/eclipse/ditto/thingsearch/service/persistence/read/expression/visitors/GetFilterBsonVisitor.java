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
package org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors;

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.DESIRED_PROPERTIES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_ATTRIBUTES_PATH;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_DEFINITION;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURES_PATH;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_F_ARRAY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_METADATA_PATH;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_THING;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.PROPERTIES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.SLASH;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.FilterFieldExpressionVisitor;

import com.mongodb.client.model.Filters;

/**
 * Creates a Mongo Bson object for field-based search criteria.
 */
public final class GetFilterBsonVisitor extends AbstractFieldBsonCreator implements FilterFieldExpressionVisitor<Bson> {

    private final Function<String, Bson> predicateFunction;

    GetFilterBsonVisitor(final Function<String, Bson> predicateFunction,
            @Nullable final List<String> authorizationSubjectIds) {

        super(authorizationSubjectIds);
        this.predicateFunction = predicateFunction;
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
        return matchValue(FIELD_ATTRIBUTES_PATH + key);
    }

    @Override
    public Bson visitFeatureDefinition(final String featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureValue(FIELD_DEFINITION);
        } else {
            return matchValue(FIELD_FEATURES_PATH + featureId + SLASH + FIELD_DEFINITION);
        }
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureValue(PROPERTIES + property);
        } else {
            return matchValue(FIELD_FEATURES_PATH + featureId + PROPERTIES + property);
        }
    }

    @Override
    public Bson visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence desiredProperty) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureValue(DESIRED_PROPERTIES + desiredProperty);
        } else {
            return matchValue(FIELD_FEATURES_PATH + featureId + DESIRED_PROPERTIES + desiredProperty);
        }
    }

    @Override
    public Bson visitMetadata(final String key) {
        return matchValue(FIELD_METADATA_PATH + key);
    }

    @Override
    Bson visitPointer(final String pointer) {
        return matchValue(pointer);
    }

    @Override
    Bson visitRootLevelField(final String fieldName) {
        return predicateFunction.apply(fieldName);
    }

    private Bson matchValue(final CharSequence key) {
        final JsonPointer pointer = JsonPointer.of(key);
        final Bson keyValueFilter = predicateFunction.apply(toDottedPath(FIELD_THING, pointer));
        return getAuthorizationBson(pointer)
                .map(authBson -> Filters.and(keyValueFilter, authBson))
                .orElse(keyValueFilter);
    }

    private Bson matchWildcardFeatureValue(final CharSequence key) {
        final JsonPointer pointer = JsonPointer.of(key);
        final Bson keyValueFilter = predicateFunction.apply(toDottedPath(pointer));
        return getFeatureWildcardAuthorizationBson(pointer)
                .map(authBson -> Filters.elemMatch(FIELD_F_ARRAY, Filters.and(keyValueFilter, authBson)))
                .orElse(keyValueFilter);
    }

}
