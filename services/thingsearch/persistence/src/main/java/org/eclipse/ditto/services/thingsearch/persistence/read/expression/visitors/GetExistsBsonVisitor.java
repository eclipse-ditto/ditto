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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES_PATH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURES_PATH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.PROPERTIES;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.model.query.expression.visitors.ExistsFieldExpressionVisitor;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

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
        return matchKey(escapeAndWrapExistsRegex(FIELD_ATTRIBUTES_PATH + key));
    }

    @Override
    public Bson visitFeature(final String featureId) {
        return matchKey(escapeAndWrapExistsRegex(FIELD_FEATURES_PATH + featureId));
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return matchKey(escapeAndWrapExistsRegex(FIELD_FEATURES_PATH + featureId + PROPERTIES + property));
    }

    @Override
    Bson visitPointer(final String pointer) {
        return getAuthorizationBson().map(authBson ->
                Filters.elemMatch(FIELD_INTERNAL, Filters.and(authBson, Filters.eq(FIELD_INTERNAL_KEY, pointer))))
                .orElseGet(() -> Filters.eq(FIELD_PATH_KEY, pointer));
    }

    @Override
    Bson visitRootLevelField(final String fieldName) {
        return Filters.exists(fieldName);
    }

    private Bson matchKey(final String keyRegex) {
        return getAuthorizationBson().map(authBson ->
                Filters.elemMatch(FIELD_INTERNAL, Filters.and(authBson,
                        Filters.regex(FIELD_INTERNAL_KEY, keyRegex))))
                .orElseGet(() -> Filters.regex(PersistenceConstants.FIELD_PATH_KEY, keyRegex));
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
