/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;

import com.mongodb.client.model.Filters;

/**
 * Creates a Mongo Bson object for field-based empty criteria.
 * A field is considered "empty" when it is absent, {@code null}, an empty array {@code []}, an empty object
 * {@code {}} or an empty string {@code ""}.
 *
 * @since 3.9.0
 */
public final class GetEmptyBsonVisitor extends AbstractFieldBsonCreator implements ExistsFieldExpressionVisitor<Bson> {

    GetEmptyBsonVisitor(@Nullable final List<String> authorizationSubjectIds) {
        super(authorizationSubjectIds);
    }

    /**
     * Creates a Mongo Bson object for field-based empty criteria.
     *
     * @param expression the expression of the resource whose emptiness is under scrutiny.
     * @param authorizationSubjectIds subject IDs for authorization filtering.
     * @return the complete Bson for the field-based empty criteria.
     */
    public static Bson apply(final ExistsFieldExpression expression, final List<String> authorizationSubjectIds) {
        return expression.acceptExistsVisitor(new GetEmptyBsonVisitor(authorizationSubjectIds));
    }

    /**
     * Creates a Mongo Bson object for field-based empty criteria without authorization filtering.
     *
     * @param expression the expression of the resource whose emptiness is under scrutiny.
     * @return the complete Bson for the field-based empty criteria.
     */
    public static Bson apply(final ExistsFieldExpression expression) {
        return apply(expression, null);
    }

    @Override
    public Bson visitAttribute(final String key) {
        return matchEmptyKey(FIELD_ATTRIBUTES_PATH + key);
    }

    @Override
    public Bson visitFeature(final String featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            // for wildcard features, check that no feature ID exists at all (absent or null)
            final String featureIdPath = toDottedPath(FIELD_F_ARRAY, List.of(JsonKey.of(FIELD_FEATURE_ID)));
            return Filters.or(
                    Filters.eq(featureIdPath, null),
                    Filters.eq(featureIdPath, "")
            );
        } else {
            return matchEmptyKey(FIELD_FEATURES_PATH + featureId);
        }
    }

    @Override
    public Bson visitFeatureDefinition(final String featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureEmptyKey(SLASH + FIELD_DEFINITION);
        } else {
            return matchEmptyKey(FIELD_FEATURES_PATH + featureId + SLASH + FIELD_DEFINITION);
        }
    }

    @Override
    public Bson visitFeatureProperties(final CharSequence featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureEmptyKey(SLASH + FIELD_PROPERTIES);
        } else {
            return matchEmptyKey(FIELD_FEATURES_PATH + featureId + SLASH + FIELD_PROPERTIES);
        }
    }

    @Override
    public Bson visitFeatureDesiredProperties(final CharSequence featureId) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureEmptyKey(SLASH + FIELD_DESIRED_PROPERTIES);
        } else {
            return matchEmptyKey(FIELD_FEATURES_PATH + featureId + SLASH + FIELD_DESIRED_PROPERTIES);
        }
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureEmptyKey(PROPERTIES + property);
        } else {
            return matchEmptyKey(FIELD_FEATURES_PATH + featureId + PROPERTIES + property);
        }
    }

    @Override
    public Bson visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        if (FEATURE_ID_WILDCARD.equals(featureId)) {
            return matchWildcardFeatureEmptyKey(DESIRED_PROPERTIES + property);
        } else {
            return matchEmptyKey(FIELD_FEATURES_PATH + featureId + DESIRED_PROPERTIES + property);
        }
    }

    @Override
    Bson visitPointer(final String key) {
        return matchEmptyKey(key);
    }

    @Override
    Bson visitRootLevelField(final String fieldName) {
        return createEmptyBson(fieldName);
    }

    @Override
    public Bson visitMetadata(final String key) {
        return matchEmptyKey(FIELD_METADATA_PATH + key);
    }

    private Bson matchEmptyKey(final CharSequence key) {
        final JsonPointer pointer = JsonPointer.of(key);
        final String dottedPath = toDottedPath(FIELD_THING, pointer);

        final Bson emptyBson = createEmptyBson(dottedPath);
        return getAuthorizationBson(pointer)
                .map(authBson -> Filters.and(emptyBson, authBson))
                .orElse(emptyBson);
    }

    private Bson matchWildcardFeatureEmptyKey(final CharSequence featureRelativeKey) {
        final JsonPointer pointer = JsonPointer.of(featureRelativeKey);
        final String dottedPath = toDottedPath(pointer);

        final Bson emptyBson = createEmptyBson(dottedPath);
        return getFeatureWildcardAuthorizationBson(pointer)
                .map(authBson -> Filters.elemMatch(FIELD_F_ARRAY, Filters.and(emptyBson, authBson)))
                .orElseGet(() -> Filters.elemMatch(FIELD_F_ARRAY, emptyBson));
    }

    /**
     * Creates the BSON filter for "empty" semantics on the given field path.
     * The filter matches when the field is:
     * <ul>
     *   <li>absent or {@code null} ({@code $eq: null} — MongoDB matches both absent and null)</li>
     *   <li>an empty array ({@code $eq: []})</li>
     *   <li>an empty object ({@code $eq: {}})</li>
     *   <li>an empty string ({@code $eq: ""})</li>
     * </ul>
     *
     * <p>Note: {@code $eq: null} in MongoDB matches both fields with value {@code null} AND absent fields,
     * so a separate {@code $exists: false} branch is not needed.</p>
     *
     * @param fieldPath the MongoDB dotted field path.
     * @return the BSON filter.
     */
    private static Bson createEmptyBson(final String fieldPath) {
        return Filters.or(
                Filters.eq(fieldPath, null),
                Filters.eq(fieldPath, Collections.emptyList()),
                Filters.eq(fieldPath, new BsonDocument()),
                Filters.eq(fieldPath, "")
        );
    }

}
