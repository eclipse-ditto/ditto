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
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.FilterFieldExpressionVisitor;

/**
 * The SQL counterpart of {@code GetFilterBsonVisitor} (thingsearch/service): turns a {@code FilterFieldExpression} +
 * resolved {@link SqlPredicate} into the AST boolean condition over {@code search_things st}.
 * <p>
 * <b>Side-by-side comment map (Mongo → here):</b>
 * <ul>
 *   <li>{@code visitAttribute:79-81} → {@link #visitAttribute}</li>
 *   <li>{@code visitFeatureDefinition:84-90}/{@code visitFeatureIdProperty:93-99}/
 *       {@code visitFeatureIdDesiredProperty:102-108} (wildcard {@code *} → {@code matchWildcardFeatureValue}) →
 *       {@link #visitFeatureDefinition}/{@link #visitFeatureIdProperty}/{@link #visitFeatureIdDesiredProperty}</li>
 *   <li>{@code visitMetadata:111-113} → {@link #visitMetadata}; {@code visitPointer:116-118} → {@link #visitPointer}</li>
 *   <li>{@code visitRootLevelField:121-123} (predicate on the field, NO auth — §3.3 exception 1) →
 *       {@link #visitRootLevelField}</li>
 *   <li>{@code matchValue:125-131} ({@code and(keyValueFilter, authBson)}) → {@link #matchValue}; a
 *       {@link SqlPredicate#matchesMissing() null-operand} predicate ORs {@code NOT EXISTS(wpath)} onto the probe
 *       (Mongo's {@code {f: null}} matches an absent field), a {@link SqlPredicate#neverMatches() never} predicate
 *       renders {@code false}</li>
 *   <li>{@code matchWildcardFeatureValue:133-139} ({@code elemMatch(f, and(keyValueFilter, authBson))}, per-f_id) →
 *       {@link #matchWildcardFeatureValue}; the null-operand form anchors on the feature boundary row
 *       ({@code wpath = '/features/*'}, one per feature, auth on its {@code f_id}) and accepts a feature whose property
 *       rows match OR do not exist at all</li>
 * </ul>
 */
final class GetFilterSqlVisitor extends AbstractFieldSqlCreator implements FilterFieldExpressionVisitor<SqlExpression> {

    private static final String FEATURE_ID_WILDCARD = "*";
    private static final String WILDCARD_ALIAS = "a";
    /** The alias of a second row of the SAME feature as {@link #WILDCARD_ALIAS} ({@code b.f_id = a.f_id}). */
    private static final String SAME_FEATURE_ALIAS = "b";
    private static final String THING_ALIAS = "sf";

    private final SqlPredicate predicate;

    private GetFilterSqlVisitor(final SqlPredicate predicate, @Nullable final List<String> authorizationSubjectIds) {
        super(authorizationSubjectIds);
        this.predicate = predicate;
    }

    static SqlExpression apply(final FilterFieldExpression expression, final SqlPredicate predicate,
            @Nullable final List<String> authorizationSubjectIds) {
        return expression.acceptFilterVisitor(new GetFilterSqlVisitor(predicate, authorizationSubjectIds));
    }

    @Override
    public SqlExpression visitAttribute(final String key) {
        return matchValue(JsonPointer.of("/attributes/" + key));
    }

    @Override
    public SqlExpression visitFeatureDefinition(final String featureId) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureValue(JsonPointer.of("definition"));
        }
        return matchValue(JsonPointer.of("/features/" + featureId + "/definition"));
    }

    @Override
    public SqlExpression visitFeatureIdProperty(final String featureId, final String property) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureValue(JsonPointer.of("/properties/" + property));
        }
        return matchValue(JsonPointer.of("/features/" + featureId + "/properties/" + property));
    }

    @Override
    public SqlExpression visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence desiredProperty) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureValue(JsonPointer.of("/desiredProperties/" + desiredProperty));
        }
        return matchValue(JsonPointer.of("/features/" + featureId + "/desiredProperties/" + desiredProperty));
    }

    @Override
    public SqlExpression visitMetadata(final String key) {
        return matchValue(JsonPointer.of("/_metadata/" + key));
    }

    @Override
    public SqlExpression visitPointer(final String pointer) {
        return matchValue(JsonPointer.of(pointer));
    }

    @Override
    public SqlExpression visitRootLevelField(final String fieldName) {
        // §3.3 exception 1: root-mapped columns carry NO per-field auth; the predicate is applied straight to the column.
        final SqlExpression column = rootColumn(fieldName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown root-level search field: " + fieldName));
        return predicate.columnCondition(column);
    }

    // ---- thing-level (Mongo matchValue) ---------------------------------------------------------------------------

    private SqlExpression matchValue(final JsonPointer pointer) {
        if (predicate.neverMatches()) {
            // gt(null)/lt(null): no probe and no auth recheck — false AND anything is false.
            return Sql.boolLiteral(false);
        }
        final String wpath = wpathOf(pointer);
        final SqlExpression core;
        if (predicate.scopedNegation()) {
            // ne = and(ne, exists): the leaf must exist AND no leaf equals the value (multikey parity).
            final SqlExpression exists = Sql.exists(flatProbe(THING_ALIAS, wpath));
            final SqlExpression noneEqual =
                    Sql.notExists(flatProbe(THING_ALIAS, wpath, predicate.flatCondition(THING_ALIAS)));
            core = Sql.and(exists, noneEqual);
        } else {
            final SqlExpression match =
                    Sql.exists(flatProbe(THING_ALIAS, wpath, predicate.flatCondition(THING_ALIAS)));
            // in(…, null)/ge(null)/le(null): Mongo's {f: null} also matches an ABSENT field — no row at wpath at all.
            core = predicate.matchesMissing() ? Sql.or(match, Sql.notExists(flatProbe(THING_ALIAS, wpath))) : match;
        }
        return thingLevelAuth(pointer).map(authFilter -> Sql.and(core, authFilter)).orElse(core);
    }

    // ---- wildcard feature (Mongo matchWildcardFeatureValue → elemMatch, per-f_id scoped §3.5) ----------------------

    private SqlExpression matchWildcardFeatureValue(final JsonPointer propertyPointer) {
        if (predicate.neverMatches()) {
            return Sql.boolLiteral(false);
        }
        final String wpath = wildcardWpath(propertyPointer);
        if (predicate.scopedNegation()) {
            // Per-f_id scoped ne (§3.5): EXISTS a (auth on a.f_id) ∧ NOT EXISTS b within the SAME feature (b.f_id=a.f_id).
            final List<SqlExpression> outerConds = new ArrayList<>();
            outerConds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)));
            outerConds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, WPATH), Sql.text(wpath)));
            wildcardFeatureAuth(WILDCARD_ALIAS, propertyPointer).ifPresent(outerConds::add);
            outerConds.add(Sql.notExists(sameFeatureProbe(wpath, predicate.flatCondition(SAME_FEATURE_ALIAS))));
            return Sql.exists(Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, WILDCARD_ALIAS)
                    .where(Sql.and(outerConds)).build());
        }
        if (predicate.matchesMissing()) {
            // Mongo elemMatch(f, and({…: null}, auth)): SOME visible feature has the value, a null or NO such property.
            // A feature without the property has no row at the wildcard wpath, so anchor on its boundary row a
            // (wpath = '/features/*', exactly one per feature, auth on a.f_id) and probe the property rows b of the
            // SAME feature: a match, or none at all.
            final SqlExpression valueOrMissing = Sql.or(
                    Sql.exists(sameFeatureProbe(wpath, predicate.flatCondition(SAME_FEATURE_ALIAS))),
                    Sql.notExists(sameFeatureProbe(wpath)));
            final List<SqlExpression> conds = new ArrayList<>();
            conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)));
            conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, WPATH), Sql.text(FEATURES_WILDCARD_PREFIX)));
            conds.add(valueOrMissing);
            wildcardFeatureAuth(WILDCARD_ALIAS, propertyPointer).ifPresent(conds::add);
            return Sql.exists(Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, WILDCARD_ALIAS)
                    .where(Sql.and(conds)).build());
        }
        // Positive wildcard predicate: value + auth coupled to the SAME feature row (a.f_id).
        final List<SqlExpression> conds = new ArrayList<>();
        conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)));
        conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, WPATH), Sql.text(wpath)));
        conds.add(predicate.flatCondition(WILDCARD_ALIAS));
        wildcardFeatureAuth(WILDCARD_ALIAS, propertyPointer).ifPresent(conds::add);
        return Sql.exists(Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, WILDCARD_ALIAS)
                .where(Sql.and(conds)).build());
    }

    /**
     * A probe over the rows {@code b} of the SAME feature as the outer row {@code a}:
     * {@code SELECT 1 FROM search_flat b WHERE b.thing_id = a.thing_id AND b.f_id = a.f_id AND b.wpath = $wpath
     * [AND extra …]}.
     */
    private static Select sameFeatureProbe(final String wpath, final SqlExpression... extraConditions) {
        final List<SqlExpression> conds = new ArrayList<>();
        conds.add(Sql.eq(Sql.col(SAME_FEATURE_ALIAS, THING_ID), Sql.col(WILDCARD_ALIAS, THING_ID)));
        conds.add(Sql.eq(Sql.col(SAME_FEATURE_ALIAS, F_ID), Sql.col(WILDCARD_ALIAS, F_ID)));
        conds.add(Sql.eq(Sql.col(SAME_FEATURE_ALIAS, WPATH), Sql.text(wpath)));
        conds.addAll(Arrays.asList(extraConditions));
        return Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, SAME_FEATURE_ALIAS).where(Sql.and(conds)).build();
    }

    private static boolean isWildcard(final CharSequence featureId) {
        return FEATURE_ID_WILDCARD.contentEquals(featureId);
    }
}
