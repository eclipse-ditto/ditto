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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query;

import java.util.Objects;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.FilterFieldExpressionVisitor;

/**
 * The {@code search_flat.wpath} value a filter on a field is translated to, i.e. exactly the value
 * {@link GetFilterSqlVisitor} binds as {@code <alias>.wpath = $n}. Used to derive the predicate of an
 * operator-configured per-path index: a partial index {@code WHERE wpath = '<p>'} is only provable for a query whose
 * bound wpath equals {@code <p>} byte for byte.
 * <p>
 * Mirrors {@link GetFilterSqlVisitor}'s pointer construction case by case (pinned by {@code FilterFieldWpathTest},
 * which compares the result with the translator's own binds).
 * </p>
 */
public final class FilterFieldWpath {

    private static final String FEATURE_ID_WILDCARD = "*";

    private FilterFieldWpath() {
        throw new AssertionError("no instances");
    }

    /**
     * @param expression a filter field, e.g. from {@code ThingsFieldExpressionFactory#filterBy(String)}.
     * @return the {@code wpath} the translator compares a filter on this field against.
     * @throws IllegalArgumentException if the field maps to a {@code search_things} column (e.g. {@code thingId},
     * {@code namespace}) and therefore has no {@code search_flat} rows.
     */
    public static String of(final FilterFieldExpression expression) {
        return Objects.requireNonNull(expression, "expression").acceptFilterVisitor(new Visitor());
    }

    private static final class Visitor implements FilterFieldExpressionVisitor<String> {

        @Override
        public String visitAttribute(final String key) {
            return AbstractFieldSqlCreator.wpathOf(JsonPointer.of("/attributes/" + key));
        }

        @Override
        public String visitFeatureDefinition(final String featureId) {
            if (isWildcard(featureId)) {
                return AbstractFieldSqlCreator.wildcardWpath(JsonPointer.of("definition"));
            }
            return AbstractFieldSqlCreator.wpathOf(JsonPointer.of("/features/" + featureId + "/definition"));
        }

        @Override
        public String visitFeatureIdProperty(final String featureId, final String property) {
            if (isWildcard(featureId)) {
                return AbstractFieldSqlCreator.wildcardWpath(JsonPointer.of("/properties/" + property));
            }
            return AbstractFieldSqlCreator.wpathOf(
                    JsonPointer.of("/features/" + featureId + "/properties/" + property));
        }

        @Override
        public String visitFeatureIdDesiredProperty(final CharSequence featureId,
                final CharSequence desiredProperty) {
            if (isWildcard(featureId)) {
                return AbstractFieldSqlCreator.wildcardWpath(JsonPointer.of("/desiredProperties/" + desiredProperty));
            }
            return AbstractFieldSqlCreator.wpathOf(
                    JsonPointer.of("/features/" + featureId + "/desiredProperties/" + desiredProperty));
        }

        @Override
        public String visitMetadata(final String key) {
            return AbstractFieldSqlCreator.wpathOf(JsonPointer.of("/_metadata/" + key));
        }

        @Override
        public String visitSimple(final String fieldName) {
            // AbstractFieldSqlCreator#visitSimple: slash-prefixed -> flat probe on that pointer, else a doc-row column.
            if (fieldName.startsWith("/")) {
                return AbstractFieldSqlCreator.wpathOf(JsonPointer.of(fieldName));
            }
            throw new IllegalArgumentException("The field <" + fieldName + "> is a search_things column, not a "
                    + "search_flat path.");
        }

        private static boolean isWildcard(final CharSequence featureId) {
            return FEATURE_ID_WILDCARD.contentEquals(featureId);
        }
    }

}
