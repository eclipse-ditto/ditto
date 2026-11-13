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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Case-sensitive {@code like} against the {@code sf_trgm} trigram index, on the production search schema in a real
 * PostgreSQL 16 (Testcontainers).
 * <p>
 * {@code search_flat.val_text} is {@code COLLATE "C"}, but {@code sf_trgm} indexes the expression
 * {@code (val_text COLLATE "C.utf8")}. The planner only uses an index for a clause whose collation matches the index
 * column's collation, so a plain {@code sf.val_text LIKE '%x%'} can never be served by {@code sf_trgm}. The translator
 * keeps the plain predicate for prefix-only patterns (the {@code sf_text (wpath, val_text)} B-tree serves those as a
 * range scan) and for patterns without a run of three consecutive literal letters/digits (no guaranteed trigram: at
 * most padded, typically unselective ones, and for unanchored short runs none, so {@code sf_trgm} could only be read
 * in full), and matches every other pattern under {@code C.utf8}.
 * </p>
 * <p>
 * <b>Plan tests</b> EXPLAIN the production {@code count} statement ({@link SearchQueryAssembler#count}, sudo form) with
 * the pattern bound exactly as the persistence binds it, under {@code plan_cache_mode = force_custom_plan} (the
 * shipped search pool setting) and {@code enable_seqscan = off} (index eligibility on a small fixture), after
 * {@code VACUUM ANALYZE}. <b>Result-parity tests</b> compare the translated predicate with the pre-change plain
 * {@code COLLATE "C"} predicate on ASCII and non-ASCII values (umlauts, {@code ß}, combining characters, CJK, an
 * astral-plane emoji, escaped SQL wildcards), both through {@code sf_trgm} (the competing B-trees dropped in a
 * rolled-back transaction) and through the heap filter (no bitmap scans).
 * </p>
 * <p>
 * Skipped when no Docker daemon is reachable (the {@link #startContainer() Assume} guard).
 * </p>
 */
public final class PostgresLikeTrigramIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresLikeTrigramIT.class);

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    /** Things in the plan fixture; every 1000th carries a {@code building-<i>} name, the rest an md5 hex string. */
    private static final int PLAN_THINGS = 20_000;

    private static final String ENABLE_SEQSCAN_OFF = "SET enable_seqscan = off";
    private static final String NO_BITMAP_SCANS = "SET enable_bitmapscan = off";

    /**
     * The pre-change translation of {@code like} on a flat path (sudo form): the plain {@code COLLATE "C"} column. The
     * wildcard-feature form differs only in the alias, which does not change the result set.
     */
    private static final String LEGACY_LIKE_SQL = "SELECT st.thing_id FROM search_things st WHERE EXISTS "
            + "(SELECT 1 FROM search_flat sf WHERE sf.thing_id = st.thing_id AND sf.wpath = $1 "
            + "AND sf.val_text LIKE $2 ESCAPE '\\') ORDER BY st.thing_id";

    /** Parity words (thing {@code parity:w<index>}), written with escapes so the source encoding cannot alter them. */
    private static final List<String> WORDS = List.of(
            "\u00c4rger",          // 0  Ärger
            "\u00e4rger",          // 1  ärger
            "ARGER",               // 2
            "Aerger",              // 3
            "Stra\u00dfe",         // 4  Straße
            "STRASSE",             // 5
            "strasse",             // 6
            "caf\u00e9",           // 7  café, precomposed U+00E9
            "cafe\u0301",          // 8  café, e + U+0301 COMBINING ACUTE ACCENT
            "\u00c9clair",         // 9  Éclair
            "\u00e9clair",         // 10 éclair
            "\u0130stanbul",       // 11 İstanbul
            "istanbul",            // 12
            "na\u00efve",          // 13 naïve
            "\u65e5\u672c\u8a9e",  // 14 日本語
            "\ud83d\ude42-smile",  // 15 🙂-smile (astral plane, 4 UTF-8 bytes)
            "a%b",                 // 16
            "a_b",                 // 17
            "a\\b",                // 18 a\b
            "47110",               // 19
            "14711",               // 20
            "x47");                // 21

    private final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(Map.of(
            "thingId", "_id",
            "namespace", "_namespace"));
    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresLikeTrigramIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        loadPlanFixture();
        loadParityFixture();
        execute("VACUUM ANALYZE search_things");
        execute("VACUUM ANALYZE search_flat");
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    // ============================================================================================ plans (eligibility)

    @Test
    public void infixLikeUsesTheTrigramIndex() {
        final String plan = explainCount(cf.fieldCriteria(fef.filterByAttribute("name"), cf.like("*uildin*")));
        assertTrigramIndexCondition(plan, "~~ '%uildin%'");
    }

    @Test
    public void suffixLikeUsesTheTrigramIndex() {
        final String plan = explainCount(cf.fieldCriteria(fef.filterByAttribute("name"), cf.like("*ding-5000")));
        assertTrigramIndexCondition(plan, "~~ '%ding-5000'");
    }

    @Test
    public void singleCharWildcardLikeUsesTheTrigramIndex() {
        final String plan = explainCount(cf.fieldCriteria(fef.filterByAttribute("name"), cf.like("?uildin?-5000")));
        assertTrigramIndexCondition(plan, "~~ '_uildin_-5000'");
    }

    @Test
    public void threeLiteralCharacterInfixUsesTheTrigramIndex() {
        // Exactly three literal letters: the shortest run that always yields a trigram.
        final String plan = explainCount(cf.fieldCriteria(fef.filterByAttribute("name"), cf.like("*uil*")));
        assertTrigramIndexCondition(plan, "~~ '%uil%'");
    }

    @Test
    public void threeLiteralCharacterSuffixIsTrigramEligible() {
        // "*147" matches ~0.1% of the zips, so on this fixture the planner may rightly prefer the wpath B-tree.
        // Eligibility only: with the B-trees that could drive the probe dropped (in a rolled-back transaction), a
        // clause that can use sf_trgm gets it, one that cannot falls back to the disabled seq scan.
        final String plan = explainCountWithoutCompetingBtrees(
                cf.fieldCriteria(fef.filterByAttribute("zip"), cf.like("*147")));
        assertTrigramIndexCondition(plan, "~~ '%147'");
    }

    @Test
    public void trigramLessPatternsKeepThePlainPredicateAndTheWpathScan() {
        // Without a run of three consecutive literal letters/digits there is no guaranteed trigram: *ab*, *?, *---*
        // and ????? yield none (sf_trgm could only be read in full, which the planner mis-costs on large paths), *47
        // only a padded, unselective "47 ". These patterns keep the plain predicate (sf_trgm is not a candidate) and
        // the wpath-bounded B-tree plan they had before.
        final Map<String, String> patternsByField = new LinkedHashMap<>();
        patternsByField.put("*ab*", "name");
        patternsByField.put("*?", "name");
        patternsByField.put("*---*", "name");
        patternsByField.put("?????", "zip");
        patternsByField.put("*47", "zip");
        patternsByField.forEach((wildcards, attribute) -> {
            final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute(attribute), cf.like(wildcards));
            final String sql = SearchQueryAssembler.count(criteria, null, 0, 0).sql();
            assertThat(sql).as(wildcards).contains("sf.val_text LIKE $2").doesNotContain("COLLATE");
            final String plan = explainCount(criteria);
            assertThat(plan).as("plan of %s:%n%s", wildcards, plan)
                    .doesNotContain("sf_trgm")
                    .contains("Index Cond: (wpath = '/attributes/" + attribute + "'::text)");
        });
    }

    @Test
    public void infixLikeOnWildcardFeaturePathUsesTheTrigramIndex() {
        final String plan = explainCount(
                cf.fieldCriteria(fef.filterByFeatureProperty("*", "prop0"), cf.like("*uildin*")));
        assertTrigramIndexCondition(plan, "~~ '%uildin%'");
    }

    @Test
    public void prefixLikeKeepsTheBtreeRangeScan() {
        final String plan = explainCount(cf.fieldCriteria(fef.filterByAttribute("zip"), cf.like("47*")));
        assertThat(plan).as("plan:%n%s", plan)
                .contains("sf_text")
                .contains("(val_text >= '47'::text)")
                .contains("(val_text < '48'::text)")
                .doesNotContain("sf_trgm");
    }

    @Test
    public void infixIlikeStillUsesTheTrigramIndex() {
        final String plan = explainCount(cf.fieldCriteria(fef.filterByAttribute("name"), cf.ilike("*UILDIN*")));
        assertTrigramIndexCondition(plan, "~~* '%UILDIN%'");
    }

    // ============================================================================================ result parity

    @Test
    public void likeResultsMatchThePlainCCollationPredicate() {
        final Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("*rger", words(0, 1, 3));
        expected.put("\u00c4*", words(0));                 // Ä* — case-sensitive: not ärger
        expected.put("\u00e4*", words(1));                 // ä*
        expected.put("?rger", words(0, 1));                // ? is one character, also a 2-byte one
        expected.put("*\u00df*", words(4));                // *ß* — no folding to ss
        expected.put("*SS*", words(5));
        expected.put("*ss*", words(6));
        expected.put("*\u00e9*", words(7, 10));            // precomposed é only
        expected.put("*e\u0301", words(8));                // decomposed é only (no normalization)
        expected.put("caf?", words(7));                    // café is 4 code points precomposed ...
        expected.put("caf??", words(8));                   // ... and 5 decomposed
        expected.put("*\u0130*", words(11));               // *İ*
        expected.put("*stanbul", words(11, 12));
        expected.put("na?ve", words(13));
        expected.put("*\u00e4rger", words(1));             // *ärger — trigram-bearing, non-ASCII run
        expected.put("*a\u00dfe", words(4));               // *aße
        expected.put("*\u65e5\u672c\u8a9e*", words(14));    // *日本語*
        expected.put("*\u672c*", words(14));               // *本*
        expected.put("?-smile", words(15));                // an astral code point is one ?
        expected.put("*\ud83d\ude42*", words(15));
        expected.put("a%b", words(16));                    // literal %, not a wildcard
        expected.put("a_b", words(17));                    // literal _, not a wildcard
        expected.put("a\\b", words(18));                   // literal backslash
        expected.put("*%*", words(16));
        expected.put("47*", words(19));
        expected.put("*47", words(21));
        expected.put("*47*", words(19, 20, 21));
        expected.put("*", words(allIndexes()));

        final List<FilterFieldExpression> fields = List.of(
                fef.filterByAttribute("word"),
                fef.filterByFeatureProperty("*", "word"));
        for (final FilterFieldExpression field : fields) {
            for (final Map.Entry<String, Set<String>> entry : expected.entrySet()) {
                final Criteria criteria = cf.fieldCriteria(field, cf.like(entry.getKey()));
                final String description = field + " like " + entry.getKey();
                final RenderedSql translated = selectThingIds(criteria);

                final Set<String> legacy = legacyThingIds(translated);
                final Set<String> heapPath = thingIds(translated, NO_BITMAP_SCANS);
                final Set<String> trigramPath = withoutCompetingBtrees(connection -> rows(connection, translated))
                        .stream()
                        .filter(id -> id.startsWith("parity:"))
                        .collect(Collectors.toCollection(TreeSet::new));

                assertThat(legacy).as("legacy %s", description).isEqualTo(entry.getValue());
                assertThat(heapPath).as("translated, no bitmap scans: %s", description).isEqualTo(legacy);
                assertThat(trigramPath).as("translated, sf_trgm only: %s", description).isEqualTo(legacy);
                if (translated.sql().contains("COLLATE \"C.utf8\" LIKE")) {
                    // the sf_trgm-only path really went through the trigram index (lossy match + recheck)
                    final String plan = String.join("\n", withoutCompetingBtrees(connection ->
                            rows(connection, explainOf(translated))));
                    assertThat(plan).as("plan of %s", description)
                            .contains("Bitmap Index Scan on sf_trgm")
                            .contains("Index Cond: ((val_text)::text ~~ ");
                }
            }
        }
    }

    // ============================================================================================ fixture

    private static void loadPlanFixture() {
        execute("INSERT INTO search_things (thing_id, namespace, revision, thing) "
                + "SELECT 'plan:t-' || i, 'plan', 1, '{}'::jsonb FROM generate_series(1, " + PLAN_THINGS + ") AS i");
        // /attributes/name: md5 hex (never contains the letters of "uildin"), every 1000th a building-<i> name.
        execute("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'plan:t-' || i, '/attributes/name', '/attributes/name', NULL, 0, 3, "
                + "CASE WHEN i % 1000 = 0 THEN 'building-' || i ELSE md5(i::text) END "
                + "FROM generate_series(1, " + PLAN_THINGS + ") AS i");
        // /attributes/zip: five-digit strings, ~1/90 of them start with 47.
        execute("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'plan:t-' || i, '/attributes/zip', '/attributes/zip', NULL, 0, 3, "
                + "(10000 + (i * 7919) % 90000)::text "
                + "FROM generate_series(1, " + PLAN_THINGS + ") AS i");
        // Three features per thing, each prop0 leaf as the flattener writes it: the exact row and the /features/* row.
        execute("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'plan:t-' || i, '/features/f' || k || '/properties/prop0', wp.w, 'f' || k, 0, 3, "
                + "CASE WHEN (i * 3 + k) % 1000 = 0 THEN 'building-' || i || '-' || k ELSE md5(i || '-' || k) END "
                + "FROM generate_series(1, " + PLAN_THINGS + ") AS i, generate_series(0, 2) AS k, "
                + "LATERAL (VALUES ('/features/f' || k || '/properties/prop0'), ('/features/*/properties/prop0')) "
                + "AS wp(w)");
        // Ten filler attributes per thing with the bench corpus's value-<n> strings.
        execute("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'plan:t-' || i, '/attributes/a' || j, '/attributes/a' || j, NULL, 0, 3, "
                + "'value-' || ((i * 31 + j * 7919) % 100000) "
                + "FROM generate_series(1, " + PLAN_THINGS + ") AS i, generate_series(0, 9) AS j");
    }

    private static void loadParityFixture() {
        for (int i = 0; i < WORDS.size(); i++) {
            final String thingId = "parity:w" + i;
            final String word = WORDS.get(i);
            execute("INSERT INTO search_things (thing_id, namespace, revision, thing) "
                    + "VALUES ($1, 'parity', 1, '{}'::jsonb)", thingId);
            insertFlatText(thingId, "/attributes/word", "/attributes/word", null, word);
            insertFlatText(thingId, "/features/f/properties/word", "/features/f/properties/word", "f", word);
            insertFlatText(thingId, "/features/f/properties/word", "/features/*/properties/word", "f", word);
        }
    }

    private static void insertFlatText(final String thingId, final String path, final String wpath,
            final String fId, final String value) {
        if (fId == null) {
            execute("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                    + "VALUES ($1, $2, $3, NULL, 0, 3, $4)", thingId, path, wpath, value);
        } else {
            execute("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                    + "VALUES ($1, $2, $3, $4, 0, 3, $5)", thingId, path, wpath, fId, value);
        }
    }

    private static Set<String> words(final int... indexes) {
        final Set<String> thingIds = new TreeSet<>();
        for (final int index : indexes) {
            thingIds.add("parity:w" + index);
        }
        return thingIds;
    }

    private static int[] allIndexes() {
        final int[] all = new int[WORDS.size()];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return all;
    }

    // ============================================================================================ helpers

    /**
     * Asserts that {@code sf_trgm} serves the pattern: a bitmap scan on it whose index condition is the pattern match
     * on the index expression. A bare "Bitmap Index Scan on sf_trgm" is not enough — without a usable clause the
     * planner can still read the whole partial index ({@code WHERE val_text IS NOT NULL}) with no index condition.
     */
    private static void assertTrigramIndexCondition(final String plan, final String operatorAndPattern) {
        final List<String> lines = List.of(plan.split("\n"));
        final int scan = lines.stream().filter(line -> line.contains("Bitmap Index Scan on sf_trgm")).findFirst()
                .map(lines::indexOf)
                .orElse(-1);
        assertThat(scan).as("sf_trgm bitmap scan in plan:%n%s", plan).isNotNegative();
        assertThat(lines.get(scan + 1).trim())
                .as("sf_trgm index condition in plan:%n%s", plan)
                .isEqualTo("Index Cond: ((val_text)::text " + operatorAndPattern + "::text)");
    }

    /** EXPLAINs the production sudo {@code count} statement for the criteria and returns the plan text. */
    private static String explainCount(final Criteria criteria) {
        final RenderedSql rendered = SearchQueryAssembler.count(criteria, null, 0, 0);
        final String plan = String.join("\n", query(connection -> rows(connection, explainOf(rendered)),
                ENABLE_SEQSCAN_OFF));
        log("plan", rendered, plan);
        return plan;
    }

    /** Like {@link #explainCount}, but with only {@code sf_trgm} left to drive the flat probe. */
    private static String explainCountWithoutCompetingBtrees(final Criteria criteria) {
        final RenderedSql rendered = SearchQueryAssembler.count(criteria, null, 0, 0);
        final String plan = String.join("\n", withoutCompetingBtrees(connection ->
                rows(connection, explainOf(rendered))));
        log("plan without competing B-trees", rendered, plan);
        return plan;
    }

    /**
     * Runs the body in a transaction that drops every {@code search_flat} B-tree able to drive the flat probe
     * ({@code sf_text}, {@code sf_exists}, the primary key), with {@code enable_seqscan = off}, and rolls it back: a
     * clause {@code sf_trgm} can serve gets it, any other one falls back to the disabled seq scan.
     */
    private static List<String> withoutCompetingBtrees(final Function<Connection, Flux<String>> body) {
        final List<String> setup = List.of(
                "DROP INDEX sf_text",
                "DROP INDEX sf_exists",
                "ALTER TABLE search_flat DROP CONSTRAINT search_flat_pkey",
                "SET LOCAL plan_cache_mode = force_custom_plan",
                "SET LOCAL enable_seqscan = off");
        return Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Mono.from(connection.beginTransaction())
                                .thenMany(Flux.fromIterable(setup)
                                        .concatMap(sql -> Flux.from(connection.createStatement(sql).execute())
                                                .flatMap(Result::getRowsUpdated)))
                                .thenMany(body.apply(connection))
                                .collectList()
                                .flatMapMany(rows -> Mono.from(connection.rollbackTransaction())
                                        .thenMany(Flux.fromIterable(rows))),
                        Connection::close)
                .collectList()
                .block();
    }

    private static RenderedSql explainOf(final RenderedSql rendered) {
        return new RenderedSql("EXPLAIN " + rendered.sql(), rendered.binds());
    }

    /** @return the first column of every row of the rendered statement, as text. */
    private static Flux<String> rows(final Connection connection, final RenderedSql rendered) {
        return Flux.from(rendered.applyTo(connection.createStatement(rendered.sql())).execute())
                .flatMap(result -> result.map((row, meta) -> row.get(0, String.class)));
    }

    /** Debug-logs the plan (the assertion messages carry it on failure). */
    private static void log(final String label, final RenderedSql rendered, final String plan) {
        LOGGER.debug("{}:\n{}\n{}\n{}", label, rendered.sql(), rendered.bindValues(), plan);
    }

    /** @return the production translation of the criteria (sudo) as a {@code SELECT st.thing_id} statement. */
    private static RenderedSql selectThingIds(final Criteria criteria) {
        return Sql.render(Sql.select()
                .column(Sql.col(AbstractFieldSqlCreator.DOC_ALIAS, AbstractFieldSqlCreator.THING_ID))
                .from("search_things", AbstractFieldSqlCreator.DOC_ALIAS)
                .where(CreateSqlVisitor.sudoApply(criteria).predicate())
                .orderByAsc(Sql.col(AbstractFieldSqlCreator.DOC_ALIAS, AbstractFieldSqlCreator.THING_ID))
                .build());
    }

    /** Runs the pre-change plain-collation predicate with the translated statement's wpath and pattern binds. */
    private static Set<String> legacyThingIds(final RenderedSql translated) {
        final List<Object> binds = translated.bindValues();
        assertThat(binds).hasSize(2);
        return query(connection -> Flux.from(connection.createStatement(LEGACY_LIKE_SQL)
                        .bind("$1", binds.get(0))
                        .bind("$2", binds.get(1))
                        .execute())
                .flatMap(result -> result.map((row, meta) -> row.get(0, String.class))))
                .stream()
                .filter(id -> id.startsWith("parity:"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> thingIds(final RenderedSql rendered, final String setting) {
        return query(connection -> rows(connection, rendered), setting)
                .stream()
                .filter(id -> id.startsWith("parity:"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Runs the query on one connection after {@code plan_cache_mode = force_custom_plan} (as the shipped search pool)
     * and the given session settings.
     */
    private static List<String> query(final Function<Connection, Flux<String>> body,
            final String... settings) {
        final List<String> statements = new ArrayList<>();
        statements.add("SET plan_cache_mode = force_custom_plan");
        statements.addAll(List.of(settings));
        return Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Flux.fromIterable(statements)
                                .concatMap(sql -> Flux.from(connection.createStatement(sql).execute())
                                        .flatMap(Result::getRowsUpdated))
                                .thenMany(body.apply(connection)),
                        Connection::close)
                .collectList()
                .block();
    }

    private static void execute(final String sql, final Object... binds) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> {
                            final Statement statement = connection.createStatement(sql);
                            for (int i = 0; i < binds.length; i++) {
                                statement.bind("$" + (i + 1), binds[i]);
                            }
                            return Flux.from(statement.execute()).flatMap(Result::getRowsUpdated).then();
                        },
                        Connection::close)
                .block();
    }

}
