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

import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.ThingFlattener;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;

/**
 * The SQL counterpart of {@code GetEmptyBsonVisitor} (thingsearch/service, 3.9.0): {@code empty(path)} matches when the
 * value is absent, {@code null}, an empty array, an empty object or an empty string.
 * <p>
 * <b>Transcription of {@code createEmptyBson:191-198}</b> ({@code or(eq(null), eq([]), eq({}), eq(""))}) onto the flat
 * table, using the flattener's null-rank / empty-array-stub / empty-object rows (§3.2, §3.5):
 * <ul>
 *   <li>absent → {@code NOT EXISTS(wpath)};</li>
 *   <li>null → a {@code type_rank = 1} row;</li>
 *   <li>empty array → a {@code type_rank = 5} stub row (the flattener emits rank 5 only for empty / array-of-array);</li>
 *   <li>empty string → a {@code val_text = ''} row;</li>
 *   <li>empty object → a {@code type_rank = 4} row with NO deeper {@code path} (a non-empty object also has a rank-4
 *       row, so the deeper-{@code path} anti-probe via {@code starts_with} is required to avoid a false positive).</li>
 * </ul>
 * Concrete paths carry the thing-level auth recheck ({@code matchEmptyKey}); root-mapped columns do not (§3.3
 * exception 1). The wildcard-feature forms ({@code matchWildcardFeatureEmptyKey}, Mongo
 * {@code elemMatch(f, and(createEmptyBson(path), auth))}) anchor on the feature boundary row {@code a}
 * ({@code wpath = '/features/*'}, exactly one per feature, auth on {@code a.f_id}) and probe the rows {@code b} of the
 * SAME feature at the wildcard wpath: an empty marker — the deeper-{@code path} anti-probe scoped to that feature via
 * {@code c.wpath} — OR no row at all ({@code $eq: null} matches a missing key, so a feature that LACKS the key
 * matches while a thing without features does not). {@code empty(features/*)} itself is "no feature boundary row".
 * </p>
 * <p>
 * <b>Divergence (documented): {@code empty()} over-matches on direct array-of-array content, even for concrete
 * paths.</b> The {@code emptyArray} branch of {@link #emptyCore(String)} matches on the mere EXISTENCE of any
 * {@code type_rank = 5} ({@link ThingFlattener#TYPE_ARRAY}) row at {@code wpath} — but per the flattener's
 * array-of-array stub rule (plan §3.2: "the ONLY stub: direct array-of-array inner content … store the inner array
 * as a {@code type_rank=5} row without value"; see {@code ThingFlattener#walk}), a NON-empty direct array-of-arrays
 * such as {@code x=[[1,2],[3]]} ALSO emits one valueless rank-5 stub row per outer element at the very same
 * {@code wpath} as a literal {@code x=[]} would. The two cases are indistinguishable at the flat-row level: both
 * present as "a rank-5 row exists at this wpath, with no deeper path". Consequently {@code empty(x)} spuriously
 * matches {@code x=[[1,2],[3]]} on Postgres, where Mongo's {@code GetEmptyBsonVisitor} matches ONLY the literal empty
 * array {@code []}. Rationale for accepting the divergence: disambiguating would require either (a) a distinct type
 * rank for "array-of-array stub" vs. "genuinely empty array" (a schema change affecting every rank-5 consumer,
 * incl. sort's empty-array-to-null-rank exception, §3.2), or (b) an additional per-row marker column — both out of
 * scope for the flat-model transcription; allow-listed as {@code empty-overmatch-array-of-array}.
 * </p>
 */
final class GetEmptySqlVisitor extends AbstractFieldSqlCreator implements ExistsFieldExpressionVisitor<SqlExpression> {

    private static final String FEATURE_ID_WILDCARD = "*";
    private static final String THING_ALIAS = "sf";
    private static final String DEEPER_ALIAS = "c";

    private GetEmptySqlVisitor(@Nullable final List<String> authorizationSubjectIds) {
        super(authorizationSubjectIds);
    }

    static SqlExpression apply(final ExistsFieldExpression expression,
            @Nullable final List<String> authorizationSubjectIds) {
        return expression.acceptExistsVisitor(new GetEmptySqlVisitor(authorizationSubjectIds));
    }

    @Override
    public SqlExpression visitAttribute(final String key) {
        return matchEmptyKey(JsonPointer.of("/attributes/" + key));
    }

    @Override
    public SqlExpression visitFeature(final String featureId) {
        if (isWildcard(featureId)) {
            // Mongo: "no feature id exists at all" (or(eq(f.id,null),eq(f.id,""))). Flat: no feature-boundary row.
            return Sql.notExists(flatProbe(THING_ALIAS, FEATURES_WILDCARD_PREFIX));
        }
        return matchEmptyKey(JsonPointer.of("/features/" + featureId));
    }

    @Override
    public SqlExpression visitFeatureDefinition(final String featureId) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureEmptyKey(JsonPointer.of("/definition"));
        }
        return matchEmptyKey(JsonPointer.of("/features/" + featureId + "/definition"));
    }

    @Override
    public SqlExpression visitFeatureProperties(final CharSequence featureId) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureEmptyKey(JsonPointer.of("/properties"));
        }
        return matchEmptyKey(JsonPointer.of("/features/" + featureId + "/properties"));
    }

    @Override
    public SqlExpression visitFeatureDesiredProperties(final CharSequence featureId) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureEmptyKey(JsonPointer.of("/desiredProperties"));
        }
        return matchEmptyKey(JsonPointer.of("/features/" + featureId + "/desiredProperties"));
    }

    @Override
    public SqlExpression visitFeatureIdProperty(final String featureId, final String property) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureEmptyKey(JsonPointer.of("/properties/" + property));
        }
        return matchEmptyKey(JsonPointer.of("/features/" + featureId + "/properties/" + property));
    }

    @Override
    public SqlExpression visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        if (isWildcard(featureId)) {
            return matchWildcardFeatureEmptyKey(JsonPointer.of("/desiredProperties/" + property));
        }
        return matchEmptyKey(JsonPointer.of("/features/" + featureId + "/desiredProperties/" + property));
    }

    @Override
    public SqlExpression visitMetadata(final String key) {
        return matchEmptyKey(JsonPointer.of("/_metadata/" + key));
    }

    @Override
    public SqlExpression visitPointer(final String key) {
        return matchEmptyKey(JsonPointer.of(key));
    }

    @Override
    public SqlExpression visitRootLevelField(final String fieldName) {
        // createEmptyBson on a doc-row TEXT column: NULL or empty string (array/object branches can't match); NO auth.
        final SqlExpression column = rootColumn(fieldName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown root-level search field: " + fieldName));
        return Sql.or(Sql.isNull(column), Sql.eq(column, Sql.text("")));
    }

    private SqlExpression matchEmptyKey(final JsonPointer pointer) {
        final String wpath = wpathOf(pointer);
        final SqlExpression core = emptyCore(wpath);
        return thingLevelAuth(pointer).map(authFilter -> Sql.and(core, authFilter)).orElse(core);
    }

    private SqlExpression matchWildcardFeatureEmptyKey(final JsonPointer propertyPointer) {
        final String wpath = wildcardWpath(propertyPointer);
        // Mongo elemMatch(f, and(createEmptyBson(path), auth)): SOME feature visible to the subjects holds an empty
        // marker at the path OR has no such key ($eq: null matches a missing field). A feature without the key has no
        // row at the wildcard wpath, so anchor on its boundary row a (wpath = '/features/*', exactly one per feature,
        // auth on a.f_id) and probe the rows b of the SAME feature: an empty marker, or none at all.
        final SqlExpression emptyOrMissing = Sql.or(
                Sql.exists(sameFeatureProbe(wpath, sameFeatureEmptyMarkers(wpath))),
                Sql.notExists(sameFeatureProbe(wpath)));
        final List<SqlExpression> conds = new ArrayList<>();
        conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)));
        conds.add(Sql.eq(Sql.col(WILDCARD_ALIAS, WPATH), Sql.text(FEATURES_WILDCARD_PREFIX)));
        conds.add(emptyOrMissing);
        wildcardFeatureAuth(WILDCARD_ALIAS, propertyPointer).ifPresent(conds::add);
        return Sql.exists(Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, WILDCARD_ALIAS)
                .where(Sql.and(conds)).build());
    }

    /** The OR of empty markers for a CONCRETE path (with the deeper-object anti-probe). */
    private SqlExpression emptyCore(final String wpath) {
        final SqlExpression absent = Sql.notExists(flatProbe(THING_ALIAS, wpath));
        final SqlExpression nullRank = Sql.exists(flatProbe(THING_ALIAS, wpath, rankIs(ThingFlattener.TYPE_NULL)));
        final SqlExpression emptyArray = Sql.exists(flatProbe(THING_ALIAS, wpath, rankIs(ThingFlattener.TYPE_ARRAY)));
        final SqlExpression emptyString =
                Sql.exists(flatProbe(THING_ALIAS, wpath, Sql.eq(Sql.col(THING_ALIAS, SqlValues.VAL_TEXT), Sql.text(""))));
        final SqlExpression emptyObject = Sql.and(
                Sql.exists(flatProbe(THING_ALIAS, wpath, rankIs(ThingFlattener.TYPE_OBJECT))),
                Sql.notExists(deeperPathProbe(wpath)));
        return Sql.or(absent, nullRank, emptyArray, emptyString, emptyObject);
    }

    /**
     * The OR of empty markers on the same-feature row {@code b} at a wildcard wpath, WITH the deeper-object anti-probe
     * scoped to that feature ({@link #sameFeatureDeeperPathProbe}) — the per-feature twin of {@link #emptyCore}; the
     * absent branch is the caller's {@code NOT EXISTS} same-feature probe.
     */
    private static SqlExpression sameFeatureEmptyMarkers(final String wpath) {
        final SqlExpression emptyObject = Sql.and(
                sameFeatureRankIs(ThingFlattener.TYPE_OBJECT),
                Sql.notExists(sameFeatureDeeperPathProbe(wpath)));
        return Sql.or(
                sameFeatureRankIs(ThingFlattener.TYPE_NULL),
                sameFeatureRankIs(ThingFlattener.TYPE_ARRAY),
                Sql.eq(Sql.col(SAME_FEATURE_ALIAS, SqlValues.VAL_TEXT), Sql.text("")),
                emptyObject);
    }

    private static SqlExpression rankIs(final short typeRank) {
        return Sql.eq(Sql.col(THING_ALIAS, TYPE_RANK), Sql.integer(typeRank));
    }

    private static SqlExpression sameFeatureRankIs(final short typeRank) {
        return Sql.eq(Sql.col(SAME_FEATURE_ALIAS, TYPE_RANK), Sql.integer(typeRank));
    }

    /** {@code SELECT 1 FROM search_flat c WHERE c.thing_id = st.thing_id AND starts_with(c.path, $wpath || '/')}. */
    private static Select deeperPathProbe(final String wpath) {
        return Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, DEEPER_ALIAS)
                .where(Sql.and(
                        Sql.eq(Sql.col(DEEPER_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)),
                        Sql.func("starts_with", Sql.col(DEEPER_ALIAS, PATH), Sql.text(wpath + "/"))))
                .build();
    }

    /**
     * {@code SELECT 1 FROM search_flat c WHERE c.thing_id = a.thing_id AND c.f_id = a.f_id AND
     * starts_with(c.wpath, $wpath || '/')}: a deeper row of the SAME feature under the wildcard wpath (every row of a
     * feature subtree carries its {@code /features/*} wpath twin, so the wildcard address is complete).
     */
    private static Select sameFeatureDeeperPathProbe(final String wpath) {
        return Sql.select().column(Sql.raw("1")).from(FLAT_TABLE, DEEPER_ALIAS)
                .where(Sql.and(
                        Sql.eq(Sql.col(DEEPER_ALIAS, THING_ID), Sql.col(WILDCARD_ALIAS, THING_ID)),
                        Sql.eq(Sql.col(DEEPER_ALIAS, F_ID), Sql.col(WILDCARD_ALIAS, F_ID)),
                        Sql.func("starts_with", Sql.col(DEEPER_ALIAS, WPATH), Sql.text(wpath + "/"))))
                .build();
    }

    private static boolean isWildcard(final CharSequence featureId) {
        return FEATURE_ID_WILDCARD.contentEquals(featureId);
    }
}
