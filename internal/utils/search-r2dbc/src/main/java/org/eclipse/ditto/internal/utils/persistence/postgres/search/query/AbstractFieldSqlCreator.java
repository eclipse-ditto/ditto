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
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.JsonPointerSegments;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Shared machinery for the SQL field-expression visitors — the transcription base of
 * {@code AbstractFieldBsonCreator} (thingsearch/service). It owns the auth builder and the flat-table probe / doc-row
 * column primitives; the concrete {@link GetFilterSqlVisitor}/{@link GetExistsSqlVisitor}/{@link GetEmptySqlVisitor}
 * subclasses supply the per-operator core condition.
 * <p>
 * <b>Side-by-side comment map (Mongo → here):</b>
 * {@code visitSimple:88-92} → {@link #visitSimple(String)}; {@code toDottedPath(FIELD_THING, pointer)} (the value key)
 * → {@link #wpathOf(JsonPointer)} (RFC-6901 flat {@code wpath}); {@code getAuthorizationBson} → {@link #thingLevelAuth};
 * {@code getFeatureWildcardAuthorizationBson}/{@code elemMatch(FIELD_F_ARRAY, …)} → {@link #wildcardFeatureAuth} +
 * {@link #wildcardWpath}; the per-feature coupling of {@code elemMatch} (value or emptiness and auth on the SAME
 * feature) → {@link #sameFeatureProbe} anchored on the feature boundary row.
 * </p>
 */
abstract class AbstractFieldSqlCreator {

    static final String FLAT_TABLE = "search_flat";
    static final String DOC_ALIAS = AuthFilterSqlBuilder.DOC_ALIAS;
    static final String THING_ID = "thing_id";
    static final String WPATH = "wpath";
    static final String PATH = "path";
    static final String F_ID = "f_id";
    static final String TYPE_RANK = "type_rank";

    /** The wpath prefix the flattener emits for every {@code /features/<id>/…} row's wildcard variant (Task C1). */
    static final String FEATURES_WILDCARD_PREFIX = "/features/*";

    /** The alias of the wildcard-feature row ({@code a}) the {@code features/*} forms anchor on. */
    static final String WILDCARD_ALIAS = "a";
    /** The alias of a second row of the SAME feature as {@link #WILDCARD_ALIAS} ({@code b.f_id = a.f_id}). */
    static final String SAME_FEATURE_ALIAS = "b";

    /**
     * The two ROOT-mapped system fields → doc-row columns (§3.5, §3.3 exception 1): no flat probe, no per-field auth.
     * The user-facing RQL→internal mapping ({@code simple-field-mappings}) is applied upstream by the
     * {@code ThingsFieldExpressionFactory}; these are the two internal names that resolve to columns.
     */
    private static final Map<String, String> ROOT_FIELD_TO_COLUMN = Map.of(
            "_id", THING_ID,
            "_namespace", "namespace");

    final AuthFilterSqlBuilder auth;

    AbstractFieldSqlCreator(@Nullable final List<String> authorizationSubjectIds) {
        this.auth = new AuthFilterSqlBuilder(authorizationSubjectIds);
    }

    // ---- visitSimple dispatch (Mongo AbstractFieldBsonCreator.visitSimple) ----------------------------------------

    /**
     * @param fieldName the resolved simple field name — slash-prefixed ⇒ slash-mapped flat probe with auth;
     * otherwise root-mapped doc-row column with NO auth (§3.3 exception 1).
     * @return the field condition.
     */
    public final SqlExpression visitSimple(final String fieldName) {
        return fieldName.startsWith("/") ? visitPointer(fieldName) : visitRootLevelField(fieldName);
    }

    abstract SqlExpression visitPointer(String pointer);

    abstract SqlExpression visitRootLevelField(String fieldName);

    // ---- shared primitives ----------------------------------------------------------------------------------------

    /** @return the doc-row column for a root-mapped field, or empty if not one of the two mapped columns. */
    static Optional<SqlExpression> rootColumn(final String fieldName) {
        final String column = ROOT_FIELD_TO_COLUMN.get(fieldName);
        return Optional.ofNullable(column).map(c -> Sql.col(DOC_ALIAS, c));
    }

    /** @return the RFC-6901 flat {@code wpath} for a full queried pointer (parity with {@code ThingFlattener}). */
    static String wpathOf(final JsonPointer pointer) {
        return JsonPointerSegments.toWpath(pointer);
    }

    /** @return the {@code /features/*}-wildcarded flat {@code wpath} for a feature-relative property pointer. */
    static String wildcardWpath(final JsonPointer propertyPointer) {
        return FEATURES_WILDCARD_PREFIX + JsonPointerSegments.toWpath(propertyPointer);
    }

    Optional<SqlExpression> thingLevelAuth(final JsonPointer pointer) {
        return auth.thingLevelAuth(pointer);
    }

    Optional<SqlExpression> wildcardFeatureAuth(final String flatAlias, final JsonPointer propertyPointer) {
        return auth.wildcardFeatureAuth(flatAlias, propertyPointer);
    }

    /**
     * A flat-table probe subquery {@code SELECT 1 FROM search_flat <alias> WHERE <alias>.thing_id = st.thing_id
     * AND <alias>.wpath = $wpath [AND extra …]}.
     */
    static Select flatProbe(final String alias, final String wpath, final SqlExpression... extraConditions) {
        final List<SqlExpression> conds = new ArrayList<>();
        conds.add(Sql.eq(Sql.col(alias, THING_ID), Sql.col(DOC_ALIAS, THING_ID)));
        conds.add(Sql.eq(Sql.col(alias, WPATH), Sql.text(wpath)));
        for (final SqlExpression extra : extraConditions) {
            conds.add(extra);
        }
        return Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, alias).where(Sql.and(conds)).build();
    }

    /**
     * A probe over the rows {@code b} of the SAME feature as the outer wildcard row {@code a}:
     * {@code SELECT 1 FROM search_flat b WHERE b.thing_id = a.thing_id AND b.f_id = a.f_id AND b.wpath = $wpath
     * [AND extra …]}. With {@code a} on the feature boundary row ({@code wpath = '/features/*'}, exactly one per
     * feature, auth on {@code a.f_id}) this is the flat form of Mongo's {@code elemMatch}: what the property rows say
     * (a value, an empty marker, or that there are none) stays coupled to the feature the auth was checked on.
     */
    static Select sameFeatureProbe(final String wpath, final SqlExpression... extraConditions) {
        final List<SqlExpression> conds = new ArrayList<>();
        conds.add(Sql.eq(Sql.col(SAME_FEATURE_ALIAS, THING_ID), Sql.col(WILDCARD_ALIAS, THING_ID)));
        conds.add(Sql.eq(Sql.col(SAME_FEATURE_ALIAS, F_ID), Sql.col(WILDCARD_ALIAS, F_ID)));
        conds.add(Sql.eq(Sql.col(SAME_FEATURE_ALIAS, WPATH), Sql.text(wpath)));
        for (final SqlExpression extra : extraConditions) {
            conds.add(extra);
        }
        return Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, SAME_FEATURE_ALIAS).where(Sql.and(conds)).build();
    }
}
