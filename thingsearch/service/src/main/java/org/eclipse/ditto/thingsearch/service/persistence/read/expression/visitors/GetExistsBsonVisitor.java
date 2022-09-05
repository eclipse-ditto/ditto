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
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_DESIRED_PROPERTIES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURES_PATH;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURE_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_F_ARRAY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_METADATA_PATH;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_PROPERTIES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_THING;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.PROPERTIES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.SLASH;

import java.util.List;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;

import com.mongodb.client.model.Filters;

/**
 * Creates a Mongo Bson object for field-based exists criteria.
 */
public final class GetExistsBsonVisitor extends AbstractFieldBsonCreator implements ExistsFieldExpressionVisitor<Bson> {

    GetExistsBsonVisitor(@Nullable final List<String> authorizationSubjectIds) {
        super(authorizationSubjectIds);
    }

    /**
     * Creates a Mongo Bson object for field-based exists criteria.
     *
     * @param expression the expression of the resource whose existence is under scrutiny.
     * @return the complete Bson for the field-based exists criteria
     */
    public static Bson apply(final ExistsFieldExpression expression, final List<String> authorizationSubjectIds) {
        return expression.acceptExistsVisitor(new GetExistsBsonVisitor(authorizationSubjectIds));
    }

    /**
     * Creates a Mongo Bson object for field-based exists criteria.
     *
     * @param expression the expression of the resource whose existence is under scrutiny.
     * @return the complete Bson for the field-based exists criteria
     */
    public static Bson apply(final ExistsFieldExpression expression) {
        return apply(expression, null);
    }

    @Override
    public Bson visitAttribute(final String key) {
        return matchKey(FIELD_ATTRIBUTES_PATH + key);
    }

    @Override
    public Bson visitFeature(final String featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            // any feature exists
            return Filters.exists(toDottedPath(FIELD_F_ARRAY, List.of(JsonKey.of(FIELD_FEATURE_ID))));
        } else {
            return matchKey(FIELD_FEATURES_PATH + featureId);
        }
    }

    @Override
    public Bson visitFeatureDefinition(final String featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureKey(SLASH + FIELD_DEFINITION);
        } else {
            return matchKey(FIELD_FEATURES_PATH + featureId + SLASH + FIELD_DEFINITION);
        }
    }

    @Override
    public Bson visitFeatureProperties(final CharSequence featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureKey(SLASH + FIELD_PROPERTIES);
        } else {
            return matchKey(FIELD_FEATURES_PATH + featureId + SLASH + FIELD_PROPERTIES);
        }
    }

    @Override
    public Bson visitFeatureDesiredProperties(final CharSequence featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureKey(SLASH + FIELD_DESIRED_PROPERTIES);
        } else {
            return matchKey(FIELD_FEATURES_PATH + featureId + SLASH + FIELD_DESIRED_PROPERTIES);
        }
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureKey(PROPERTIES + property);
        } else {
            return matchKey(FIELD_FEATURES_PATH + featureId + PROPERTIES + property);
        }
    }

    @Override
    public Bson visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureKey(DESIRED_PROPERTIES + property);
        } else {
            return matchKey(FIELD_FEATURES_PATH + featureId + DESIRED_PROPERTIES + property);
        }
    }

    @Override
    Bson visitPointer(final String key) {
        return matchKey(key);
    }

    @Override
    Bson visitRootLevelField(final String fieldName) {
        return Filters.exists(fieldName);
    }

    private Bson matchKey(final CharSequence key) {
        final JsonPointer pointer = JsonPointer.of(key);

        return getAuthorizationBson(pointer)
                .map(authBson -> Filters.and(Filters.exists(toDottedPath(FIELD_THING, pointer)), authBson))
                .orElseGet(() -> Filters.exists(toDottedPath(FIELD_THING, pointer)));
    }

    private Bson matchWildcardFeatureKey(final CharSequence featureRelativeKey) {
        final JsonPointer pointer = JsonPointer.of(featureRelativeKey);

        return getFeatureWildcardAuthorizationBson(pointer)
                .map(authBson -> Filters.elemMatch(FIELD_F_ARRAY,
                        Filters.and(Filters.exists(toDottedPath(pointer)), authBson)))
                .orElseGet(() -> Filters.elemMatch(FIELD_F_ARRAY, Filters.exists(toDottedPath(pointer))));
    }

    @Override
    public Bson visitMetadata(final String key) {
        return matchKey(FIELD_METADATA_PATH + key);
    }

}
