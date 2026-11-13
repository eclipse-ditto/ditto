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
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Builds the per-field grant/revoke authorization SQL — the 1:1 transcription of
 * {@code AbstractFieldBsonCreator.getAuthFilter}/{@code getFeatureWildcardAuthorizationBson} (thingsearch/service)
 * onto PostgreSQL JSONB auth-tree rechecks (plan §3.3).
 * <p>
 * <b>Nesting (Mongo {@code getAuthFilter}, revoke-wins / grant-inherits-down):</b> for the statically known ancestor
 * chain of a queried path, the <em>leaf</em> level is the OUTERMOST filter and the <em>root</em> level the innermost:
 * <pre>{@code
 *   NOT revoke(leaf) AND ( grant(leaf)
 *     OR ( NOT revoke(parent) AND ( grant(parent)
 *       OR ( ... OR ( NOT revoke(root) AND grant(root) ) ) ) ) )
 * }</pre>
 * Each {@code grant(p)} renders as {@code COALESCE((policy_auth #> $path::text[]) ?| $subjects::text[], false)} and
 * each {@code revoke(p)} as its {@code NOT}. The path arrays carry <em>raw</em> (un-escaped) key segments — the auth
 * JSONB is written with raw keys ({@code EvaluatedPolicy}), unlike the flat {@code wpath} columns which are RFC-6901
 * encoded. The root-only case renders {@code (NOT revoke(root) AND grant(root))} with no {@code OR} wrapper (Mongo's
 * {@code Filters.or} of a single element is semantically bare).
 * </p>
 * <p>
 * <b>Wildcard feature ({@code getFeatureWildcardAuthorizationBson}):</b> the ancestor chain is prefixed with the three
 * FIXED levels {@code [<root>, /features, /id]} BEFORE the property path, and every level is re-checked against the
 * per-row feature auth tree {@code jsonb_extract_path(st.features_auth, <flatAlias>.f_id, <segments…>, <·g|·r>)} so the
 * value match and the auth stay coupled to the SAME feature (Mongo's {@code elemMatch} parity).
 * </p>
 */
final class AuthFilterSqlBuilder {

    /** char-183 (U+00B7) prefixed grant key, matching {@code EvaluatedPolicy}/{@code SearchIndexFields.FIELD_GRANTED}. */
    static final String GRANT_KEY = "·g";
    /** char-183 (U+00B7) prefixed revoke key, matching {@code EvaluatedPolicy}/{@code SearchIndexFields.FIELD_REVOKED}. */
    static final String REVOKE_KEY = "·r";

    static final String DOC_ALIAS = "st";
    private static final String POLICY_AUTH = "policy_auth";
    private static final String FEATURES_AUTH = "features_auth";
    private static final String GLOBAL_READ = "global_read";
    private static final String F_ID = "f_id";
    private static final String JSONB_EXTRACT_PATH = "jsonb_extract_path";

    /** The three fixed levels prefixed before a wildcard-feature property path (Mongo's fixedPaths). */
    private static final List<List<String>> WILDCARD_FIXED_LEVELS = List.of(
            List.of(),               // root grants (empty pointer)
            List.of("features"),     // /features grants
            List.of("id"));          // /id (feature-level) grants

    @Nullable
    private final List<String> authorizationSubjectIds;

    AuthFilterSqlBuilder(@Nullable final List<String> authorizationSubjectIds) {
        this.authorizationSubjectIds = authorizationSubjectIds == null ? null : List.copyOf(authorizationSubjectIds);
    }

    /**
     * @return whether authorization is restricted at all (false = sudo / no-auth).
     */
    boolean isRestricted() {
        return authorizationSubjectIds != null;
    }

    /**
     * The global-read term {@code st.global_read && $subjects::text[]} (§3.3). Only meaningful when restricted.
     *
     * @return the array-overlap expression.
     */
    SqlExpression globalRead() {
        return Sql.arrayOverlap(Sql.col(DOC_ALIAS, GLOBAL_READ), subjectsParam());
    }

    /**
     * Thing-level auth over {@code st.policy_auth} for the ancestor chain of {@code pointer} (Mongo
     * {@code getAuthorizationBson} → {@code getAuthFilter}).
     *
     * @param pointer the full queried path (attributes/…, features/&lt;id&gt;/…, /policyId, …).
     * @return the auth filter, or empty when unrestricted (sudo).
     */
    Optional<SqlExpression> thingLevelAuth(final JsonPointer pointer) {
        if (authorizationSubjectIds == null) {
            return Optional.empty();
        }
        return Optional.of(thingAuthFilter(segmentsOf(pointer)));
    }

    /**
     * Wildcard-feature auth over {@code jsonb_extract_path(st.features_auth, <flatAlias>.f_id, …)} for the fixed chain
     * {@code [<root>, /features, /id]} + the property path (Mongo {@code getFeatureWildcardAuthorizationBson}).
     *
     * @param flatAlias the alias of the flat row whose {@code f_id} scopes the recheck.
     * @param propertyPointer the feature-relative property pointer (e.g. {@code /properties/temp}, {@code /definition}).
     * @return the auth filter, or empty when unrestricted (sudo).
     */
    Optional<SqlExpression> wildcardFeatureAuth(final String flatAlias, final JsonPointer propertyPointer) {
        if (authorizationSubjectIds == null) {
            return Optional.empty();
        }
        final List<List<String>> allLevels = new ArrayList<>(WILDCARD_FIXED_LEVELS);
        allLevels.addAll(collectSegmentPrefixes(propertyPointer));
        SqlExpression child = null;
        for (final List<String> levelSegments : allLevels) {
            child = wildcardLevel(flatAlias, levelSegments, child);
        }
        return Optional.of(child);
    }

    // ---- thing-level recursion (mirrors getAuthFilter(subjects, pointer)) -----------------------------------------

    private SqlExpression thingAuthFilter(final List<String> segments) {
        if (segments.isEmpty()) {
            return thingLevel(segments, null);
        }
        return thingLevel(segments, thingAuthFilter(segments.subList(0, segments.size() - 1)));
    }

    private SqlExpression thingLevel(final List<String> segments, @Nullable final SqlExpression child) {
        final SqlExpression grant = coalescedMatch(
                Sql.jsonbExtractPath(Sql.col(DOC_ALIAS, POLICY_AUTH), pathArray(segments, GRANT_KEY)));
        final SqlExpression notRevoke = Sql.not(coalescedMatch(
                Sql.jsonbExtractPath(Sql.col(DOC_ALIAS, POLICY_AUTH), pathArray(segments, REVOKE_KEY))));
        final SqlExpression or = child == null ? grant : Sql.or(grant, child);
        return Sql.and(notRevoke, or);
    }

    // ---- wildcard-feature level (mirrors getFeatureWildcardAuthorizationBson's per-level getAuthFilter) -----------

    private SqlExpression wildcardLevel(final String flatAlias, final List<String> segments,
            @Nullable final SqlExpression child) {
        final SqlExpression grant = coalescedMatch(extract(flatAlias, segments, GRANT_KEY));
        final SqlExpression notRevoke = Sql.not(coalescedMatch(extract(flatAlias, segments, REVOKE_KEY)));
        final SqlExpression or = child == null ? grant : Sql.or(grant, child);
        return Sql.and(notRevoke, or);
    }

    private SqlExpression extract(final String flatAlias, final List<String> segments, final String key) {
        final List<SqlExpression> args = new ArrayList<>();
        args.add(Sql.col(DOC_ALIAS, FEATURES_AUTH));
        args.add(Sql.col(flatAlias, F_ID));
        for (final String segment : segments) {
            args.add(Sql.text(segment));
        }
        args.add(Sql.text(key));
        return Sql.func(JSONB_EXTRACT_PATH, args);
    }

    // ---- shared bits ----------------------------------------------------------------------------------------------

    private SqlExpression coalescedMatch(final SqlExpression jsonbArray) {
        return Sql.coalesce(Sql.jsonbAnyKeyMatch(jsonbArray, subjectsParam()), Sql.boolLiteral(false));
    }

    private SqlExpression pathArray(final List<String> segments, final String key) {
        final String[] full = segments.toArray(new String[segments.size() + 1]);
        full[segments.size()] = key;
        return Sql.textArray(full);
    }

    private SqlExpression subjectsParam() {
        return Sql.textArray(authorizationSubjectIds);
    }

    private static List<String> segmentsOf(final JsonPointer pointer) {
        final List<String> segments = new ArrayList<>();
        for (final JsonKey key : pointer) {
            segments.add(key.toString());
        }
        return segments;
    }

    /**
     * All non-empty prefixes of {@code pointer} as raw-segment lists, shortest first (Mongo {@code collectPaths}).
     */
    private static List<List<String>> collectSegmentPrefixes(final JsonPointer pointer) {
        final List<String> segments = segmentsOf(pointer);
        final List<List<String>> prefixes = new ArrayList<>();
        for (int i = 1; i <= segments.size(); i++) {
            prefixes.add(List.copyOf(segments.subList(0, i)));
        }
        return prefixes;
    }
}
