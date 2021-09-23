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

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

import com.mongodb.client.model.Filters;

/**
 * Creates a Mongo Bson object for field-based exists criteria.
 */
public class GetExistsBsonVisitor extends AbstractFieldBsonCreator implements ExistsFieldExpressionVisitor<Bson> {

    private static final List<Integer> JAVASCRIPT_REGEX_SPECIAL_CHARACTERS =
            "\\^$*+?.()|{}[]".chars().boxed().collect(Collectors.toList());

    private GetExistsBsonVisitor(@Nullable final List<String> authorizationSubjectIds) {
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
        return matchKey(escapeAndWrapExistsRegex(PersistenceConstants.FIELD_ATTRIBUTES_PATH + key));
    }

    @Override
    public Bson visitFeature(final String featureId) {
        return matchKey(escapeAndWrapExistsRegex(PersistenceConstants.FIELD_FEATURES_PATH + featureId));
    }

    @Override
    public Bson visitFeatureProperties(final CharSequence featureId) {
        return matchKey(escapeAndWrapExistsRegex(
                PersistenceConstants.FIELD_FEATURES_PATH + featureId + PersistenceConstants.SLASH + PersistenceConstants.FIELD_PROPERTIES));
    }

    @Override
    public Bson visitFeatureDesiredProperties(final CharSequence featureId) {
        return matchKey(escapeAndWrapExistsRegex(
                PersistenceConstants.FIELD_FEATURES_PATH + featureId + PersistenceConstants.SLASH + PersistenceConstants.FIELD_DESIRED_PROPERTIES));
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return matchKey(escapeAndWrapExistsRegex(
                PersistenceConstants.FIELD_FEATURES_PATH + featureId + PersistenceConstants.PROPERTIES + property));
    }

    @Override
    public Bson visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        return matchKey(escapeAndWrapExistsRegex(
                PersistenceConstants.FIELD_FEATURES_PATH + featureId + PersistenceConstants.DESIRED_PROPERTIES + property));
    }

    @Override
    Bson visitPointer(final String pointer) {
        return getAuthorizationBson().map(authBson ->
                        Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL, Filters.and(authBson, Filters.eq(
                                PersistenceConstants.FIELD_INTERNAL_KEY, pointer))))
                .orElseGet(() -> Filters.eq(PersistenceConstants.FIELD_PATH_KEY, pointer));
    }

    @Override
    Bson visitRootLevelField(final String fieldName) {
        return Filters.exists(fieldName);
    }

    private Bson matchKey(final String keyRegex) {
        return getAuthorizationBson().map(authBson ->
                        Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL, Filters.and(authBson,
                                Filters.regex(PersistenceConstants.FIELD_INTERNAL_KEY, keyRegex))))
                .orElseGet(() -> Filters.regex(PersistenceConstants.FIELD_PATH_KEY, keyRegex));
    }

    @Override
    public Bson visitMetadata(final String key) {
        // search on _metadata is not supported, return filter that don't match
        return Filters.eq("nomatch");
    }

    private static String escapeAndWrapExistsRegex(final String string) {
        return FieldExpressionUtil.wrapExistsRegex(escapeJavascriptRegex(string));
    }

    private static String escapeJavascriptRegex(final CharSequence otherString) {
        return otherString.chars()
                .mapToObj(codePoint -> {
                    final StringBuilder stringBuilder = new StringBuilder();
                    if (JAVASCRIPT_REGEX_SPECIAL_CHARACTERS.contains(codePoint)) {
                        stringBuilder.append('\\');
                    }
                    return stringBuilder.appendCodePoint(codePoint).toString();
                })
                .collect(Collectors.joining());
    }

}
