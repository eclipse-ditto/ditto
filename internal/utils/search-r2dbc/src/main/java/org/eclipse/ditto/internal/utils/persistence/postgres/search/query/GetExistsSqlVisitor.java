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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;

/**
 * The SQL counterpart of {@code GetExistsBsonVisitor} (thingsearch/service): {@code exists(path)} → an {@code EXISTS}
 * probe on the leaf's {@code wpath}.
 * <p>
 * <b>Side-by-side comment map (Mongo → here):</b>
 * <ul>
 *   <li>{@code visitFeature:75-82} — {@code exists(features/*)} is a BARE {@code Filters.exists("f.id")} with NO auth
 *       (§3.3 exception 2); mapped to the flattener's feature-boundary row {@code wpath = /features/*} →
 *       {@link #visitFeature}</li>
 *   <li>{@code matchKey:139-145} ({@code and(exists(t.path), authBson)}, concrete paths DO carry auth) →
 *       {@link #matchKey}</li>
 *   <li>{@code matchWildcardFeatureKey:147-154} ({@code elemMatch(f, and(exists, auth))}) →
 *       {@link #matchWildcardFeatureKey}</li>
 *   <li>{@code visitRootLevelField:135-137} ({@code Filters.exists(fieldName)}, NO auth) → {@link #visitRootLevelField}</li>
 * </ul>
 */
final class GetExistsSqlVisitor extends AbstractFieldSqlCreator implements ExistsFieldExpressionVisitor<SqlExpression> {

    private static final String FEATURE_ID_WILDCARD = "*";
    private static final String THING_ALIAS = "sf";

    private GetExistsSqlVisitor(@Nullable final List<String> authorizationSubjectIds) {
        super(authorizationSubjectIds);
    }

    static SqlExpression apply(final ExistsFieldExpression expression,
            @Nullable final List<String> authorizationSubjectIds) {
        return expression.acceptExistsVisitor(new GetExistsSqlVisitor(authorizationSubjectIds));
    }

    @Override
    public SqlExpression visitAttribute(final String key) {
        return matchKey(JsonPointer.of("/attributes/" + key));
    }

    @Override
    public SqlExpression visitFeature(final String featureId) {
        if (isWildcard(featureId)) {
            // "any feature exists": bare probe on the feature-boundary wpath the flattener emits, NO auth (exception 2).
            return Sql.exists(flatProbe(THING_ALIAS, FEATURES_WILDCARD_PREFIX));
        }
        return matchKey(JsonPointer.of("/features/" + featureId));
    }

    @Override
    public SqlExpression visitFeatureDefinition(final String featureId) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureKey(JsonPointer.of("/definition"));
        }
        return matchKey(JsonPointer.of("/features/" + featureId + "/definition"));
    }

    @Override
    public SqlExpression visitFeatureProperties(final CharSequence featureId) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureKey(JsonPointer.of("/properties"));
        }
        return matchKey(JsonPointer.of("/features/" + featureId + "/properties"));
    }

    @Override
    public SqlExpression visitFeatureDesiredProperties(final CharSequence featureId) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureKey(JsonPointer.of("/desiredProperties"));
        }
        return matchKey(JsonPointer.of("/features/" + featureId + "/desiredProperties"));
    }

    @Override
    public SqlExpression visitFeatureIdProperty(final String featureId, final String property) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureKey(JsonPointer.of("/properties/" + property));
        }
        return matchKey(JsonPointer.of("/features/" + featureId + "/properties/" + property));
    }

    @Override
    public SqlExpression visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureKey(JsonPointer.of("/desiredProperties/" + property));
        }
        return matchKey(JsonPointer.of("/features/" + featureId + "/desiredProperties/" + property));
    }

    @Override
    public SqlExpression visitMetadata(final String key) {
        return matchKey(JsonPointer.of("/_metadata/" + key));
    }

    @Override
    public SqlExpression visitPointer(final String key) {
        return matchKey(JsonPointer.of(key));
    }

    @Override
    public SqlExpression visitRootLevelField(final String fieldName) {
        // §3.3 exception 1: Filters.exists on a doc-row column, NO auth.
        final SqlExpression column = rootColumn(fieldName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown root-level search field: " + fieldName));
        return Sql.isNotNull(column);
    }

    private SqlExpression matchKey(final JsonPointer pointer) {
        final SqlExpression exists = Sql.exists(flatProbe(THING_ALIAS, wpathOf(pointer)));
        return thingLevelAuth(pointer).map(authFilter -> Sql.and(exists, authFilter)).orElse(exists);
    }

    private SqlExpression matchWildcardFeatureKey(final JsonPointer propertyPointer) {
        final String wpath = wildcardWpath(propertyPointer);
        final List<SqlExpression> conds = new ArrayList<>();
        conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)));
        conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, WPATH), Sql.text(wpath)));
        wildcardFeatureAuth(WILDCARD_ALIAS, propertyPointer).ifPresent(conds::add);
        return Sql.exists(Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, WILDCARD_ALIAS)
                .where(Sql.and(conds)).build());
    }

    private static boolean isWildcard(final CharSequence featureId) {
        return FEATURE_ID_WILDCARD.contentEquals(featureId);
    }
}
