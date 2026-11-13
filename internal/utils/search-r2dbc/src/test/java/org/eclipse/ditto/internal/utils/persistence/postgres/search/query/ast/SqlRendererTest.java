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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

/**
 * Unit tests for the parameterized SQL AST + renderer + bind collector (Task D1).
 * <p>
 * Proves: every node type renders; the §3.3 auth-tree and §3.5 query shapes render with the exact SQL text + ordered
 * binds; {@code $n} numbering is deterministic across arbitrary nesting; and — the security contract — hostile strings
 * can only ever reach the statement as bind VALUES, never as SQL text.
 */
public final class SqlRendererTest {

    private static final String G = "·g"; // char-183-prefixed grant key
    private static final String R = "·r"; // char-183-prefixed revoke key

    // ===================================================================================================== node types

    @Test
    public void comparisonOperatorsRender() {
        assertThat(sql(Sql.eq(Sql.col("val_num"), Sql.numeric(1)))).isEqualTo("val_num = $1");
        assertThat(sql(Sql.ne(Sql.col("val_num"), Sql.numeric(1)))).isEqualTo("val_num <> $1");
        assertThat(sql(Sql.lt(Sql.col("val_num"), Sql.numeric(1)))).isEqualTo("val_num < $1");
        assertThat(sql(Sql.le(Sql.col("val_num"), Sql.numeric(1)))).isEqualTo("val_num <= $1");
        assertThat(sql(Sql.gt(Sql.col("val_num"), Sql.numeric(1)))).isEqualTo("val_num > $1");
        assertThat(sql(Sql.ge(Sql.col("val_num"), Sql.numeric(1)))).isEqualTo("val_num >= $1");
    }

    @Test
    public void qualifiedColumnAndInAndBooleanTreeRender() {
        assertThat(sql(Sql.col("st", "thing_id"))).isEqualTo("st.thing_id");
        assertThat(sql(Sql.eqAny(Sql.col("val_text"), Sql.textArray("a", "b"))))
                .isEqualTo("val_text = ANY($1::text[])");
        assertThat(sql(Sql.and(Sql.col("a"), Sql.col("b")))).isEqualTo("(a AND b)");
        assertThat(sql(Sql.or(Sql.col("a"), Sql.col("b")))).isEqualTo("(a OR b)");
        assertThat(sql(Sql.not(Sql.col("a")))).isEqualTo("NOT a");
        // single-operand AND/OR render bare (no redundant parens)
        assertThat(sql(Sql.and(Sql.col("a")))).isEqualTo("a");
    }

    @Test
    public void likeIlikeNullAndLiteralsRender() {
        assertThat(sql(Sql.like(Sql.col("val_text"), Sql.text("a%"))))
                .isEqualTo("val_text LIKE $1 ESCAPE '\\'");
        assertThat(sql(Sql.ilike(Sql.col("val_text"), Sql.text("a%"), "C.utf8")))
                .isEqualTo("val_text COLLATE \"C.utf8\" ILIKE $1 ESCAPE '\\'");
        assertThat(sql(Sql.isNull(Sql.col("val_text")))).isEqualTo("val_text IS NULL");
        assertThat(sql(Sql.isNotNull(Sql.col("val_text")))).isEqualTo("val_text IS NOT NULL");
        assertThat(sql(Sql.boolLiteral(true))).isEqualTo("true");
        assertThat(sql(Sql.boolLiteral(false))).isEqualTo("false");
        assertThat(sql(Sql.nullLiteral())).isEqualTo("NULL");
        assertThat(sql(Sql.raw("1"))).isEqualTo("1");
    }

    @Test
    public void jsonbOperatorsRender() {
        assertThat(sql(Sql.jsonbExtractPath(Sql.col("policy_auth"), Sql.textArray("attributes", "temp", G))))
                .isEqualTo("(policy_auth #> $1::text[])");
        assertThat(sql(Sql.jsonbAnyKeyMatch(Sql.col("policy_auth"), Sql.textArray("sub"))))
                .isEqualTo("policy_auth ?| $1::text[]");
        assertThat(sql(Sql.jsonbKeyExists(Sql.col("policy_auth"), Sql.text("k")))).isEqualTo("policy_auth ? $1");
        assertThat(sql(Sql.jsonbContains(Sql.col("referenced_policies"), Sql.jsonb("[{\"id\":\"x\"}]"))))
                .isEqualTo("referenced_policies @> $1::jsonb");
    }

    @Test
    public void coalesceCaseAndFunctionRender() {
        assertThat(sql(Sql.coalesce(Sql.col("k0", "type_rank"), Sql.integer(1))))
                .isEqualTo("COALESCE(k0.type_rank, $1)");
        assertThat(sql(Sql.caseWhen(
                List.of(Sql.when(Sql.eq(Sql.col("type_rank"), Sql.integer(5)), Sql.integer(1))),
                Sql.col("type_rank"))))
                .isEqualTo("CASE WHEN type_rank = $1 THEN $2 ELSE type_rank END");
        assertThat(sql(Sql.func("jsonb_extract_path", Sql.col("st", "features_auth"), Sql.col("a", "f_id"),
                Sql.text("temp")))).isEqualTo("jsonb_extract_path(st.features_auth, a.f_id, $1)");
        assertThat(sql(Sql.eqAny(Sql.col("ord"), Sql.intArray(1, 2)))).isEqualTo("ord = ANY($1::int[])");
    }

    @Test
    public void simpleSelectWithExistsRenders() {
        final Select sub = Sql.select().column(Sql.raw("1")).from("search_flat", "sf")
                .where(Sql.and(Sql.eq(Sql.col("sf", "thing_id"), Sql.col("st", "thing_id")),
                        Sql.eq(Sql.col("sf", "wpath"), Sql.text("/attributes/temp"))))
                .build();
        final Select query = Sql.select().column(Sql.col("st", "thing_id")).from("search_things", "st")
                .where(Sql.exists(sub)).build();

        final RenderedSql rendered = Sql.render(query);
        assertThat(rendered.sql()).isEqualTo("SELECT st.thing_id FROM search_things st WHERE EXISTS "
                + "(SELECT 1 FROM search_flat sf WHERE (sf.thing_id = st.thing_id AND sf.wpath = $1))");
        assertThat(rendered.bindValues()).containsExactly("/attributes/temp");
    }

    // ================================================================================= §3.3 auth-tree exact rendering

    @Test
    public void authTreeShapeRendersExactSqlAndBinds() {
        // Transcription of the §3.3 per-field grant/revoke tree for path /attributes/temp, evaluated against
        // policy_auth with subjects :s. ALL jsonb path arrays and the subjects are $n::text[] binds.
        final SqlExpression tree = Sql.and(
                revoke("attributes", "temp"),
                Sql.or(
                        grant("attributes", "temp"),
                        Sql.and(
                                revoke("attributes"),
                                Sql.or(
                                        grant("attributes"),
                                        Sql.and(revokeRoot(), grantRoot())))));

        final RenderedSql rendered = Sql.render(rebindSubjects(tree));

        assertThat(rendered.sql()).isEqualTo(
                "(NOT COALESCE((policy_auth #> $1::text[]) ?| $2::text[], false) AND "
                        + "(COALESCE((policy_auth #> $3::text[]) ?| $4::text[], false) OR "
                        + "(NOT COALESCE((policy_auth #> $5::text[]) ?| $6::text[], false) AND "
                        + "(COALESCE((policy_auth #> $7::text[]) ?| $8::text[], false) OR "
                        + "(NOT COALESCE((policy_auth #> $9::text[]) ?| $10::text[], false) AND "
                        + "COALESCE((policy_auth #> $11::text[]) ?| $12::text[], false))))))");

        // every bind is a text[]; odd = path arrays, even = subjects.
        assertThat(rendered.binds()).allMatch(b -> b.type() == SqlBindType.TEXT_ARRAY);
        assertThat((String[]) rendered.binds().get(0).value())
                .containsExactly("attributes", "temp", R);
        assertThat((String[]) rendered.binds().get(2).value())
                .containsExactly("attributes", "temp", G);
        assertThat((String[]) rendered.binds().get(8).value()).containsExactly(R);
        assertThat((String[]) rendered.binds().get(10).value()).containsExactly(G);
        assertThat((String[]) rendered.binds().get(1).value()).containsExactly("subjectA", "subjectB");
    }

    // ============================================================================ §3.5 per-f_id scoped ne() rendering

    @Test
    public void perFeatureScopedNeShapeRenders() {
        // ne(features/*/x, $v): per-feature-scoped (Mongo elemMatch parity) — EXISTS a ∧ NOT EXISTS b with b bound to
        // a.f_id, and the wildcard-feature auth chain threaded through jsonb_extract_path(features_auth, a.f_id, …).
        final SqlExpression subjects = Sql.textArray("s1");
        final SqlExpression auth = Sql.coalesce(
                Sql.jsonbAnyKeyMatch(
                        Sql.func("jsonb_extract_path", Sql.col("st", "features_auth"), Sql.col("a", "f_id"),
                                Sql.text("x"), Sql.text(G)),
                        subjects),
                Sql.boolLiteral(false));
        final Select inner = Sql.select().column(Sql.raw("1")).from("search_flat", "b")
                .where(Sql.and(
                        Sql.eq(Sql.col("b", "thing_id"), Sql.col("a", "thing_id")),
                        Sql.eq(Sql.col("b", "f_id"), Sql.col("a", "f_id")),
                        Sql.eq(Sql.col("b", "wpath"), Sql.text("/features/*/properties/x")),
                        Sql.eq(Sql.col("b", "val_num"), Sql.numeric(5))))
                .build();
        final Select outer = Sql.select().column(Sql.raw("1")).from("search_flat", "a")
                .where(Sql.and(
                        Sql.eq(Sql.col("a", "thing_id"), Sql.col("st", "thing_id")),
                        Sql.eq(Sql.col("a", "wpath"), Sql.text("/features/*/properties/x")),
                        auth,
                        Sql.notExists(inner)))
                .build();

        final RenderedSql rendered = Sql.render(Sql.exists(outer));

        assertThat(rendered.sql()).isEqualTo(
                "EXISTS (SELECT 1 FROM search_flat a WHERE (a.thing_id = st.thing_id AND a.wpath = $1 AND "
                        + "COALESCE(jsonb_extract_path(st.features_auth, a.f_id, $2, $3) ?| $4::text[], false) AND "
                        + "NOT EXISTS (SELECT 1 FROM search_flat b WHERE (b.thing_id = a.thing_id AND "
                        + "b.f_id = a.f_id AND b.wpath = $5 AND b.val_num = $6))))");
        assertThat(rendered.bindValues().get(0)).isEqualTo("/features/*/properties/x");
        assertThat(rendered.bindValues().get(1)).isEqualTo("x");
        assertThat(rendered.bindValues().get(2)).isEqualTo(G);
        assertThat(rendered.binds().get(3).type()).isEqualTo(SqlBindType.TEXT_ARRAY);
        assertThat(rendered.bindValues().get(4)).isEqualTo("/features/*/properties/x");
        assertThat(rendered.bindValues().get(5)).isEqualTo(BigDecimal.valueOf(5));
    }

    // ================================================================================= §3.5 lateral sort block + CASE

    @Test
    public void lateralSortBlockRenders() {
        final Select lateral = Sql.select()
                .column(Sql.col("type_rank")).column(Sql.col("val_num"))
                .column(Sql.col("val_text")).column(Sql.col("val_bool"))
                .from("search_flat", "s")
                .where(Sql.and(Sql.eq(Sql.col("s", "thing_id"), Sql.col("st", "thing_id")),
                        Sql.eq(Sql.col("s", "wpath"), Sql.text("/attributes/temp"))))
                .orderByAsc(Sql.col("type_rank")).orderByAsc(Sql.col("val_num"))
                .orderByAsc(Sql.col("val_text")).orderByAsc(Sql.col("val_bool"))
                .limit(Sql.raw("1"))
                .build();
        // empty-array rows sort as the null rank (rank 1) — a CASE over the lateral key.
        final SqlExpression firstKey = Sql.caseWhen(
                List.of(Sql.when(Sql.eq(Sql.col("k0", "type_rank"), Sql.integer(5)), Sql.integer(1))),
                Sql.col("k0", "type_rank"));
        final Select query = Sql.select().column(Sql.col("st", "thing_id")).from("search_things", "st")
                .leftJoinLateral(lateral, "k0")
                .orderByAsc(firstKey)
                .orderByAsc(Sql.col("k0", "val_num")).orderByAsc(Sql.col("k0", "val_text"))
                .orderByAsc(Sql.col("k0", "val_bool")).orderByAsc(Sql.col("st", "thing_id"))
                .build();

        final RenderedSql rendered = Sql.render(query);
        assertThat(rendered.sql()).isEqualTo(
                "SELECT st.thing_id FROM search_things st LEFT JOIN LATERAL (SELECT type_rank, val_num, val_text, "
                        + "val_bool FROM search_flat s WHERE (s.thing_id = st.thing_id AND s.wpath = $1) "
                        + "ORDER BY type_rank, val_num, val_text, val_bool LIMIT 1) k0 ON true "
                        + "ORDER BY CASE WHEN k0.type_rank = $2 THEN $3 ELSE k0.type_rank END, "
                        + "k0.val_num, k0.val_text, k0.val_bool, st.thing_id");
        assertThat(rendered.bindValues()).containsExactly("/attributes/temp", 5, 1);
    }

    // ============================================================================ §3.5 materialized CTE + EXISTS chain

    @Test
    public void materializedCteWithExistsChainRenders() {
        final Select selectiveLeg = Sql.select().column(Sql.col("thing_id")).from("search_flat")
                .where(Sql.and(Sql.eq(Sql.col("wpath"), Sql.text("/attributes/a")),
                        Sql.gt(Sql.col("val_num"), Sql.numeric(10))))
                .build();
        final Select cteProbe = Sql.select().column(Sql.raw("1")).from("sel")
                .where(Sql.eq(Sql.col("sel", "thing_id"), Sql.col("st", "thing_id"))).build();
        final Select flatProbe = Sql.select().column(Sql.raw("1")).from("search_flat", "sf")
                .where(Sql.and(Sql.eq(Sql.col("sf", "thing_id"), Sql.col("st", "thing_id")),
                        Sql.eq(Sql.col("sf", "wpath"), Sql.text("/attributes/b")),
                        Sql.eq(Sql.col("sf", "val_text"), Sql.text("x")))).build();
        final Select query = Sql.select().column(Sql.col("st", "thing_id")).from("search_things", "st")
                .withMaterialized("sel", selectiveLeg)
                .where(Sql.and(Sql.exists(cteProbe), Sql.exists(flatProbe)))
                .build();

        final RenderedSql rendered = Sql.render(query);
        assertThat(rendered.sql()).isEqualTo(
                "WITH sel AS MATERIALIZED (SELECT thing_id FROM search_flat WHERE (wpath = $1 AND val_num > $2)) "
                        + "SELECT st.thing_id FROM search_things st WHERE "
                        + "(EXISTS (SELECT 1 FROM sel WHERE sel.thing_id = st.thing_id) AND "
                        + "EXISTS (SELECT 1 FROM search_flat sf WHERE (sf.thing_id = st.thing_id AND "
                        + "sf.wpath = $3 AND sf.val_text = $4)))");
        // CTE binds precede the main-WHERE binds — numbering follows render order (WITH first).
        assertThat(rendered.bindValues()).containsExactly("/attributes/a", BigDecimal.valueOf(10), "/attributes/b", "x");
    }

    // ================================================================================== deterministic $n numbering

    @Test
    public void numberingIsDeterministicLeftToRightAcrossNesting() {
        final SqlExpression expr = Sql.and(
                Sql.eq(Sql.col("a"), Sql.text("1")),
                Sql.or(Sql.eq(Sql.col("b"), Sql.numeric(2)), Sql.eq(Sql.col("c"), Sql.text("3"))),
                Sql.gt(Sql.col("d"), Sql.numeric(4)));

        final RenderedSql rendered = Sql.render(expr);
        assertThat(rendered.sql()).isEqualTo("(a = $1 AND (b = $2 OR c = $3) AND d > $4)");
        assertThat(rendered.bindValues())
                .containsExactly("1", BigDecimal.valueOf(2), "3", BigDecimal.valueOf(4));
    }

    // ================================================================================ security: hostile-string binds

    @Test
    public void hostileStringsReachOnlyBindsNeverSqlText() {
        final List<String> hostile = List.of(
                "'; DROP TABLE search_things;--",
                "\") OR 1=1 --",
                "$evil');--",
                "attributes','x",
                "%_\\",
                " ·weird\"'");

        for (final String h : hostile) {
            // every bind position that accepts caller text
            assertBoundNotInlined(Sql.eq(Sql.col("val_text"), Sql.text(h)), h);
            assertBoundNotInlined(Sql.eqAny(Sql.col("val_text"), Sql.textArray(h)), h);
            assertBoundNotInlined(Sql.jsonbContains(Sql.col("referenced_policies"), Sql.jsonb(h)), h);
            assertBoundNotInlined(Sql.like(Sql.col("val_text"), Sql.text(h)), h);
            assertBoundNotInlined(Sql.ilike(Sql.col("val_text"), Sql.text(h), "C.utf8"), h);
            assertBoundNotInlined(Sql.jsonbExtractPath(Sql.col("policy_auth"), Sql.textArray("a", h)), h);
            assertBoundNotInlined(Sql.jsonbAnyKeyMatch(Sql.col("policy_auth"), Sql.textArray(h)), h);

            // identifier / raw slots MUST reject hostile text rather than inline it
            assertThatThrownBy(() -> Sql.col(h)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Sql.col("st", h)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Sql.func(h, Sql.col("a"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Sql.render(Sql.select().column(Sql.col("x")).from(h).build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        // The raw() escape hatch rejects every hostile string that carries injection syntax (quotes/$/;/comments); the
        // one hostile input that raw() accepts ("%_\\") is an inert LIKE pattern with no such syntax — and it is proven
        // above to be a bind, never a raw fragment. raw()'s guard is asserted exhaustively in the dedicated test below.
    }

    @Test
    public void rawFragmentRejectsInjectionCharactersButAcceptsConstantGrammar() {
        assertThatThrownBy(() -> Sql.raw("1); DROP TABLE t; --")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sql.raw("$1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sql.raw("x'y")).isInstanceOf(IllegalArgumentException.class);
        assertThat(sql(Sql.raw("count(*)"))).isEqualTo("count(*)");
        assertThat(sql(Sql.raw("1"))).isEqualTo("1");
    }

    // ============================================================== union, derived table, scalar subquery

    @Test
    public void unionAllBranchesRenderInOrderAndTheLimitAppliesToTheWholeUnion() {
        final Select union = Sql.select().column(Sql.raw("1")).from("search_flat", "c")
                .where(Sql.eq(Sql.col("c", "wpath"), Sql.text("/a")))
                .unionAll(Sql.select().column(Sql.raw("1")).from("search_flat", "c")
                        .where(Sql.and(Sql.eq(Sql.col("c", "wpath"), Sql.text("/b")),
                                Sql.eq(Sql.col("c", "ord"), Sql.raw("1"))))
                        .build())
                .limit(Sql.raw("pc.n + 1"))
                .build();

        final RenderedSql rendered = Sql.render(union);

        assertThat(rendered.sql()).isEqualTo("SELECT 1 FROM search_flat c WHERE c.wpath = $1 UNION ALL "
                + "SELECT 1 FROM search_flat c WHERE (c.wpath = $2 AND c.ord = 1) LIMIT pc.n + 1");
        assertThat(rendered.bindValues()).containsExactly("/a", "/b");
    }

    @Test
    public void derivedTableAndScalarSubqueryRenderParenthesised() {
        final Select pointCount = Sql.select().column(Sql.raw("count(*)"), "n")
                .from(Sql.select().column(Sql.raw("1")).from("search_flat", "c").limit(Sql.raw("50000")).build(),
                        "pr")
                .build();
        final Select pick = Sql.select()
                .column(Sql.le(Sql.col("pc", "n"),
                        Sql.scalar(Sql.select().column(Sql.raw("count(*)")).from("search_flat", "c").build())),
                        "point_wins")
                .from(pointCount, "pc")
                .build();

        assertThat(Sql.render(pick).sql()).isEqualTo("SELECT pc.n <= (SELECT count(*) FROM search_flat c) AS "
                + "point_wins FROM (SELECT count(*) AS n FROM (SELECT 1 FROM search_flat c LIMIT 50000) pr) pc");
    }

    @Test
    public void aUnionBranchMustBeAPlainSelect() {
        final Select limited = Sql.select().column(Sql.raw("1")).from("t").limit(Sql.raw("1")).build();
        assertThatThrownBy(() -> Sql.select().column(Sql.raw("1")).from("t").unionAll(limited))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void aDerivedTableNeedsAValidAlias() {
        final Select inner = Sql.select().column(Sql.raw("1")).from("t").build();
        assertThatThrownBy(() -> Sql.select().column(Sql.raw("1")).from(inner, "not an alias"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================================================================ bind application (type hints)

    @Test
    public void applyToBindsValuesAndTypedNullsInOrder() {
        final RenderedSql rendered = Sql.render(Sql.and(
                Sql.eq(Sql.col("val_text"), Sql.text("v")),
                Sql.eq(Sql.col("val_num"), Sql.numeric((BigDecimal) null)),
                Sql.jsonbContains(Sql.col("j"), Sql.jsonb("[]"))));
        final RecordingStatement statement = new RecordingStatement();

        rendered.applyTo(statement);

        assertThat(statement.bound).containsEntry(0, "v").containsEntry(2, "[]");
        assertThat(statement.boundNull).containsEntry(1, BigDecimal.class);
    }

    // ============================================================================================ helpers

    private static String sql(final SqlNode node) {
        return Sql.render(node).sql();
    }

    private static void assertBoundNotInlined(final SqlNode node, final String hostile) {
        final RenderedSql rendered = Sql.render(node);
        assertThat(rendered.sql())
                .as("hostile string <%s> must not appear in SQL text <%s>", hostile, rendered.sql())
                .doesNotContain(hostile);
        // the hostile string is present verbatim as a bound value (directly or inside a bound array)
        final boolean boundVerbatim = rendered.binds().stream().anyMatch(b -> {
            final Object v = b.value();
            if (hostile.equals(v)) {
                return true;
            }
            return v instanceof String[] arr && List.of(arr).contains(hostile);
        });
        assertThat(boundVerbatim)
                .as("hostile string <%s> must be carried as a bind value", hostile)
                .isTrue();
    }

    // Auth-tree helpers: R(...) / G(...) with a placeholder subjects param; rebindSubjects renumbers naturally.
    private static SqlExpression revoke(final String... path) {
        return Sql.not(coalesced(withKey(R, path)));
    }

    private static SqlExpression grant(final String... path) {
        return coalesced(withKey(G, path));
    }

    private static SqlExpression revokeRoot() {
        return Sql.not(coalesced(new String[] {R}));
    }

    private static SqlExpression grantRoot() {
        return coalesced(new String[] {G});
    }

    private static String[] withKey(final String key, final String... path) {
        final String[] full = new String[path.length + 1];
        System.arraycopy(path, 0, full, 0, path.length);
        full[path.length] = key;
        return full;
    }

    private static SqlExpression coalesced(final String[] pathArray) {
        return Sql.coalesce(
                Sql.jsonbAnyKeyMatch(
                        Sql.jsonbExtractPath(Sql.col("policy_auth"), Sql.textArray(pathArray)),
                        Sql.textArray("subjectA", "subjectB")),
                Sql.boolLiteral(false));
    }

    // the tree already embeds subjects params; return as-is (kept as a seam if subject dedup is added later).
    private static SqlExpression rebindSubjects(final SqlExpression tree) {
        return tree;
    }

}
