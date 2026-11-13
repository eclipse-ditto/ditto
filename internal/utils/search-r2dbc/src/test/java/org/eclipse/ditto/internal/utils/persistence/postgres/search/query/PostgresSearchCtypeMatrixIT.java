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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchPersistenceProvider;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The database prerequisites of the PostgreSQL search backend on clusters initialised with different {@code initdb}
 * locales ({@code POSTGRES_INITDB_ARGS}, Testcontainers), through the production
 * {@link PostgresSearchPersistenceProvider#bootstrapSchema()}, schema and {@code like}/{@code ilike} translation.
 * <p>
 * {@code pg_trgm} lower-cases and splits words with the database's {@code LC_CTYPE}, never with the index collation,
 * while {@code ILIKE … COLLATE "C.utf8"} rechecks with Unicode lower-casing. On a {@code C} ctype a value such as
 * {@code İstanbul} (U+0130) or {@code Kelvin} (U+212A) never yields the trigrams an {@code ilike} pattern with
 * {@code i}/{@code k} asks for, so the trigram index drops it before the recheck.
 * </p>
 * <ul>
 *     <li><b>Refused before any schema change:</b> a UTF8 database with a {@code C} ctype (exact message), a
 *     {@code SQL_ASCII} and a {@code LATIN1} database (instead of the raw "collation does not exist" error of
 *     {@code CREATE INDEX sf_trgm}), and a UTF8 database without the {@code "C.utf8"} collation.</li>
 *     <li><b>Accepted:</b> a {@code C.UTF-8} cluster, the image's own cluster ({@code en_US.utf8}) and a {@code C.UTF-8}
 *     database created inside a {@code C} cluster with the statement the refusal names. On each, the rows of every
 *     sample through {@code sf_trgm} alone (competing B-trees dropped in a rolled-back transaction, a real
 *     {@code Index Cond}) equal the rows of the heap filter (no index scans), the decisive samples return their
 *     expected things, and no code point folds differently for {@code pg_trgm} and for the recheck.</li>
 * </ul>
 * <p>
 * The clusters follow {@code -Dditto.test.postgres.image}; their {@code initdb} arguments are fixed here. Skipped when
 * no Docker daemon is reachable.
 * </p>
 */
public final class PostgresSearchCtypeMatrixIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSearchCtypeMatrixIT.class);

    /** A UTF8 cluster with LC_COLLATE and LC_CTYPE {@code C}: the CloudNativePG {@code initdb} default. */
    private static final PostgresDbResource C_CLUSTER =
            new PostgresDbResource().withInitdbArgs("--encoding=UTF8 --locale=C");
    private static final PostgresDbResource C_UTF8_CLUSTER =
            new PostgresDbResource().withInitdbArgs("--encoding=UTF8 --locale=C.UTF-8");
    /** The image's own cluster ({@code en_US.utf8} for the official image), whatever the initdb-args property says. */
    private static final PostgresDbResource IMAGE_DEFAULT_CLUSTER = new PostgresDbResource().withInitdbArgs(null);

    private static final String SQL_ASCII_DB = "sqlascii";
    private static final String LATIN1_DB = "latin1";
    private static final String REMEDY_DB = "remedy";
    private static final String NO_COLLATION_DB = "nocollation";

    private static final String C_CTYPE_REFUSAL = "PostgreSQL search schema bootstrap failed: database 'ditto' "
            + "(encoding UTF8, LC_CTYPE C) cannot host the search schema: LC_CTYPE 'C' is not UTF-8 based. The sf_trgm "
            + "trigram index behind like()/ilike() needs the libc collation \"C.utf8\", which exists only in "
            + "UTF8-encoded databases on a server whose OS provides the C.UTF-8 locale. pg_trgm also lower-cases and "
            + "splits words with the database's LC_CTYPE, so on a C/POSIX or other non-UTF-8 LC_CTYPE ilike() silently "
            + "misses matches (e.g. U+0130 or U+212A in a value against i or k in the pattern) whenever the planner "
            + "uses that index. Create the search database with UTF8 encoding and a UTF-8 ctype, e.g. CREATE DATABASE "
            + "ditto TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8'; on CloudNativePG, set "
            + "bootstrap.initdb.localeCType: C.UTF-8 (the default encoding is UTF8).";

    private static final String THING_PREFIX = "ctype:w";

    private static final List<String> WORDS = List.of(
            "İstanbul",                          // 0  İstanbul (U+0130 LATIN CAPITAL LETTER I WITH DOT ABOVE)
            "istanbul",                               // 1
            "ISTANBUL",                               // 2
            "ıstanbul",                          // 3  ıstanbul (U+0131 LATIN SMALL LETTER DOTLESS I)
            "Kelvin",                            // 4  Kelvin, starting with U+212A KELVIN SIGN
            "kelvin",                                 // 5
            "Ärger",                             // 6  Ärger
            "ärger",                             // 7  ärger
            "ARGER",                                  // 8
            "Aerger",                                 // 9
            "Привет",   // 10 Привет
            "привет",   // 11 привет
            "ПРИВЕТ",   // 12 ПРИВЕТ
            "日本語",                     // 13 日本語
            "Straße",                            // 14 Straße
            "STRASSE",                                // 15
            "σοφία",         // 16 σοφία
            "οδός",               // 17 οδός (final sigma U+03C2)
            "Ångström",                     // 18 Ångström, starting with U+212B ANGSTROM SIGN
            "ångström");                    // 19 ångström

    private static final List<Sample> SAMPLES = List.of(
            Sample.ilike("*istanbul*", 0, 1, 2),
            Sample.ilike("*İstanbul*", 0, 1, 2),                      // *İstanbul*
            Sample.ilike("*ISTANBUL*", 0, 1, 2),
            Sample.ilike("*kelvin*", 4, 5),
            Sample.ilike("*KELVIN*", 4, 5),
            Sample.ilike("*ärger*", 6, 7),                            // *ärger*
            Sample.ilike("*ÄRGER*", 6, 7),                            // *ÄRGER*
            Sample.ilike("*привет*", 10, 11, 12), // *привет*
            Sample.ilike("*ПРИВЕТ*", 10, 11, 12), // *ПРИВЕТ*
            Sample.ilike("*日本語*", 13),                      // *日本語*
            Sample.ilike("*ångström*", 18, 19),                  // *ångström*
            Sample.like("*日本語*", 13),                       // *日本語*
            Sample.like("*İstanbul*", 0),                             // *İstanbul*
            Sample.like("*Ärger*", 6),                                // *Ärger*
            Sample.like("*Привет*", 10),     // *Привет*
            // Residual folding limits, the same on every UTF-8 ctype (documented, not pinned): no ß/ss folding, the
            // dotless ı stays apart from i, the final ς is not folded to σ. Only "index path = heap path" is asserted.
            Sample.ilikeUnpinned("*straße*"),                         // *straße*
            Sample.ilikeUnpinned("*STRASSE*"),
            Sample.ilikeUnpinned("*ıstanbul*"),                       // *ıstanbul*
            Sample.ilikeUnpinned("*οδοσ*"));           // *οδοσ*

    private static final String[] NO_INDEX_SCANS = {
            "SET enable_bitmapscan = off", "SET enable_indexscan = off", "SET enable_indexonlyscan = off"};

    private static final String LOCALE_SQL = "SELECT pg_encoding_to_char(encoding) || '/' || datcollate || '/' "
            + "|| datctype FROM pg_database WHERE datname = current_database()";

    private static final String NOTHING_CREATED_SQL = "SELECT (to_regclass('schema_version') IS NULL "
            + "AND to_regclass('search_things') IS NULL AND to_regclass('search_flat') IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm'))::text";

    /**
     * Every code point whose trigrams ({@code pg_trgm}: the database ctype) differ from those of its lower case under
     * {@code "C.utf8"} (the recheck); a false negative needs such a code point. About one second.
     */
    private static final String FOLD_MISMATCHES_SQL = "SELECT count(*)::text || ' ' "
            + "|| coalesce(string_agg(to_hex(cp), ',' ORDER BY cp), '') "
            + "FROM generate_series(1, 1114111) AS cp "
            + "WHERE cp NOT BETWEEN 55296 AND 57343 "
            + "AND show_trgm(chr(cp)) IS DISTINCT FROM show_trgm(lower(chr(cp) COLLATE \"C.utf8\"))";

    private static final ThingsFieldExpressionFactory FEF = ThingsFieldExpressionFactory.of(Map.of(
            "thingId", "_id",
            "namespace", "_namespace"));
    private static final CriteriaFactory CF = CriteriaFactory.getInstance();

    @BeforeClass
    public static void startClusters() {
        try {
            C_CLUSTER.start();
            C_UTF8_CLUSTER.start();
            IMAGE_DEFAULT_CLUSTER.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchCtypeMatrixIT", t);
        }
        final ConnectionFactory cCluster = connectionFactory(C_CLUSTER, PostgresDbResource.DATABASE);
        execute(cCluster, "CREATE DATABASE " + SQL_ASCII_DB
                + " TEMPLATE template0 ENCODING 'SQL_ASCII' LC_COLLATE 'C' LC_CTYPE 'C'");
        execute(cCluster, "CREATE DATABASE " + LATIN1_DB
                + " TEMPLATE template0 ENCODING 'LATIN1' LC_COLLATE 'C' LC_CTYPE 'C'");
        // exactly the remedy the refusal names, inside a C cluster
        execute(cCluster, "CREATE DATABASE " + REMEDY_DB
                + " TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8'");
        execute(connectionFactory(C_UTF8_CLUSTER, PostgresDbResource.DATABASE), "CREATE DATABASE " + NO_COLLATION_DB);
        // an OS without the C.UTF-8 locale (e.g. musl/Alpine images) imports no "C.utf8" collation
        execute(connectionFactory(C_UTF8_CLUSTER, NO_COLLATION_DB), "DROP COLLATION pg_catalog.\"C.utf8\"");
    }

    @AfterClass
    public static void stopClusters() {
        C_CLUSTER.stop();
        C_UTF8_CLUSTER.stop();
        IMAGE_DEFAULT_CLUSTER.stop();
    }

    @Test
    public void aCCtypeDatabaseIsRefusedWithTheExactMessageBeforeAnySchemaChange() {
        assertThat(single(connectionFactory(C_CLUSTER, PostgresDbResource.DATABASE), LOCALE_SQL)).isEqualTo("UTF8/C/C");

        assertThatThrownBy(() -> bootstrapThroughTheProvider(C_CLUSTER, PostgresDbResource.DATABASE))
                .isInstanceOf(SchemaBootException.class)
                .hasMessage(C_CTYPE_REFUSAL);
        assertNothingWasCreated(C_CLUSTER, PostgresDbResource.DATABASE);
    }

    @Test
    public void aSqlAsciiDatabaseIsRefusedInsteadOfFailingOnTheMissingCollation() {
        assertRefused(C_CLUSTER, SQL_ASCII_DB, "SQL_ASCII/C/C", "PostgreSQL search schema bootstrap failed: "
                + "database 'sqlascii' (encoding SQL_ASCII, LC_CTYPE C) cannot host the search schema: the encoding is "
                + "SQL_ASCII, not UTF8; the collation \"C.utf8\" does not exist for encoding SQL_ASCII; LC_CTYPE 'C' is "
                + "not UTF-8 based. ");
    }

    @Test
    public void aLatin1DatabaseIsRefusedInsteadOfFailingOnTheMissingCollation() {
        assertRefused(C_CLUSTER, LATIN1_DB, "LATIN1/C/C", "PostgreSQL search schema bootstrap failed: "
                + "database 'latin1' (encoding LATIN1, LC_CTYPE C) cannot host the search schema: the encoding is "
                + "LATIN1, not UTF8; the collation \"C.utf8\" does not exist for encoding LATIN1; LC_CTYPE 'C' is not "
                + "UTF-8 based. ");
    }

    @Test
    public void aUtf8DatabaseWithoutTheCUtf8CollationIsRefused() {
        assertRefused(C_UTF8_CLUSTER, NO_COLLATION_DB, "UTF8/C.UTF-8/C.UTF-8", "PostgreSQL search schema bootstrap "
                + "failed: database 'nocollation' (encoding UTF8, LC_CTYPE C.UTF-8) cannot host the search schema: the "
                + "collation \"C.utf8\" does not exist for encoding UTF8. ");
    }

    @Test
    public void aCUtf8ClusterBootsAndTheTrigramIndexAgreesWithTheRecheck() {
        assertAccepted(C_UTF8_CLUSTER, PostgresDbResource.DATABASE, "UTF8/C.UTF-8/C.UTF-8");
    }

    @Test
    public void theImageDefaultClusterBootsAndTheTrigramIndexAgreesWithTheRecheck() {
        assertAccepted(IMAGE_DEFAULT_CLUSTER, PostgresDbResource.DATABASE, "UTF8/en_US.utf8/en_US.utf8");
    }

    @Test
    public void theRemedyDatabaseInsideACCtypeClusterBootsAndTheTrigramIndexAgreesWithTheRecheck() {
        assertAccepted(C_CLUSTER, REMEDY_DB, "UTF8/C.UTF-8/C.UTF-8");
    }

    private static void assertRefused(final PostgresDbResource cluster, final String database, final String locale,
            final String messageStart) {
        assertThat(single(connectionFactory(cluster, database), LOCALE_SQL)).isEqualTo(locale);

        assertThatThrownBy(() -> bootstrapThroughTheProvider(cluster, database))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageStartingWith(messageStart)
                .hasMessageContaining("CREATE DATABASE " + database + " TEMPLATE template0 ENCODING 'UTF8' "
                        + "LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8';")
                .hasMessageEndingWith("bootstrap.initdb.localeCType: C.UTF-8 (the default encoding is UTF8).");
        assertNothingWasCreated(cluster, database);
    }

    private static void assertAccepted(final PostgresDbResource cluster, final String database, final String locale) {
        final ConnectionFactory connectionFactory = connectionFactory(cluster, database);
        assertThat(single(connectionFactory, LOCALE_SQL)).isEqualTo(locale);

        bootstrapThroughTheProvider(cluster, database);

        loadSamples(connectionFactory);
        assertTrigramIndexAgreesWithTheRecheck(connectionFactory);
        assertThat(single(connectionFactory, FOLD_MISMATCHES_SQL))
                .as("count and hex list of the code points whose trigrams differ from those of their C.utf8 lower case")
                .isEqualTo("0 ");
    }

    private static void assertNothingWasCreated(final PostgresDbResource cluster, final String database) {
        assertThat(single(connectionFactory(cluster, database), NOTHING_CREATED_SQL))
                .as("no schema_version, no search table and no pg_trgm after the refusal")
                .isEqualTo("true");
    }

    private static void assertTrigramIndexAgreesWithTheRecheck(final ConnectionFactory connectionFactory) {
        SoftAssertions.assertSoftly(softly -> {
            for (final Sample sample : SAMPLES) {
                final String description = sample.operator() + "(attributes/word,\"" + sample.pattern() + "\")";
                final RenderedSql translated =
                        selectThingIds(CF.fieldCriteria(FEF.filterByAttribute("word"), sample.predicate()));
                final Set<String> heap =
                        new TreeSet<>(query(connectionFactory, c -> rows(c, translated), NO_INDEX_SCANS));
                final Set<String> trigram =
                        new TreeSet<>(withoutCompetingBtrees(connectionFactory, c -> rows(c, translated)));
                final String heapPlan = String.join("\n",
                        query(connectionFactory, c -> rows(c, explainOf(translated)), NO_INDEX_SCANS));
                final String trigramPlan = String.join("\n",
                        withoutCompetingBtrees(connectionFactory, c -> rows(c, explainOf(translated))));
                LOGGER.debug("{}\nheap plan:\n{}\nsf_trgm plan:\n{}", description, heapPlan, trigramPlan);

                softly.assertThat(heapPlan).as("heap-filter plan of %s", description).doesNotContain("sf_trgm");
                softly.assertThat(lineAfterTrigramScan(trigramPlan))
                        .as("sf_trgm index condition of %s, plan:%n%s", description, trigramPlan)
                        .startsWith("Index Cond: ((val_text)::text " + sample.indexOperator() + " '");
                softly.assertThat(trigram).as("%s through sf_trgm vs the heap filter", description).isEqualTo(heap);
                if (sample.expected() != null) {
                    softly.assertThat(heap).as("%s, expected things", description).isEqualTo(sample.expected());
                }
            }
        });
    }

    /** @return the trimmed line after {@code Bitmap Index Scan on sf_trgm}, or "" when the plan has none. */
    private static String lineAfterTrigramScan(final String plan) {
        final List<String> lines = List.of(plan.split("\n"));
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (lines.get(i).contains("Bitmap Index Scan on sf_trgm")) {
                return lines.get(i + 1).trim();
            }
        }
        return "";
    }

    private static void bootstrapThroughTheProvider(final PostgresDbResource cluster, final String database) {
        final Config config = ConfigFactory.parseString(
                        "ditto.postgresql {\n"
                                + "  uri = \"r2dbc:postgresql://" + cluster.getHost() + ":" + cluster.getPort() + "/"
                                + database + "\"\n"
                                + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                                + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                                + "  ssl.mode = disable\n"
                                + "}\n")
                .withFallback(ConfigFactory.load("postgres-search-test.conf"));
        final ActorSystem system = ActorSystem.create("PostgresSearchCtypeMatrixIT", config);
        try {
            new PostgresSearchPersistenceProvider(system, ConfigFactory.empty()).bootstrapSchema();
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }

    private static void loadSamples(final ConnectionFactory connectionFactory) {
        for (int i = 0; i < WORDS.size(); i++) {
            final String thingId = THING_PREFIX + i;
            execute(connectionFactory, "INSERT INTO search_things (thing_id, namespace, revision, thing) "
                    + "VALUES ($1, 'ctype', 1, '{}'::jsonb)", thingId);
            execute(connectionFactory, "INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                    + "VALUES ($1, '/attributes/word', '/attributes/word', NULL, 0, 3, $2)", thingId, WORDS.get(i));
        }
        execute(connectionFactory, "VACUUM ANALYZE search_things");
        execute(connectionFactory, "VACUUM ANALYZE search_flat");
    }

    private static ConnectionFactory connectionFactory(final PostgresDbResource cluster, final String database) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, cluster.getHost())
                .option(ConnectionFactoryOptions.PORT, cluster.getPort())
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(ConnectionFactoryOptions.USER, PostgresDbResource.DDL_USER)
                .option(ConnectionFactoryOptions.PASSWORD, PostgresDbResource.DDL_PASSWORD)
                .build());
    }

    /** The trigram-index-only path: competing B-trees dropped in a rolled-back transaction, seq scans off. */
    private static List<String> withoutCompetingBtrees(final ConnectionFactory connectionFactory,
            final Function<Connection, Flux<String>> body) {
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

    private static List<String> query(final ConnectionFactory connectionFactory,
            final Function<Connection, Flux<String>> body, final String... settings) {
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

    private static String single(final ConnectionFactory connectionFactory, final String sql) {
        return query(connectionFactory, connection -> Flux.from(connection.createStatement(sql).execute())
                .flatMap(result -> result.map((row, meta) -> row.get(0, String.class)))).get(0);
    }

    private static Flux<String> rows(final Connection connection, final RenderedSql rendered) {
        return Flux.from(rendered.applyTo(connection.createStatement(rendered.sql())).execute())
                .flatMap(result -> result.map((row, meta) -> row.get(0, String.class)));
    }

    private static RenderedSql explainOf(final RenderedSql rendered) {
        return new RenderedSql("EXPLAIN " + rendered.sql(), rendered.binds());
    }

    private static RenderedSql selectThingIds(final Criteria criteria) {
        return Sql.render(Sql.select()
                .column(Sql.col(AbstractFieldSqlCreator.DOC_ALIAS, AbstractFieldSqlCreator.THING_ID))
                .from("search_things", AbstractFieldSqlCreator.DOC_ALIAS)
                .where(CreateSqlVisitor.sudoApply(criteria).predicate())
                .orderByAsc(Sql.col(AbstractFieldSqlCreator.DOC_ALIAS, AbstractFieldSqlCreator.THING_ID))
                .build());
    }

    private static void execute(final ConnectionFactory connectionFactory, final String sql, final Object... binds) {
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

    private static Set<String> thingIds(final int... words) {
        return Arrays.stream(words)
                .mapToObj(word -> THING_PREFIX + word)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** One {@code like}/{@code ilike} on {@code attributes/word}; {@code expected == null}: documented, not pinned. */
    private record Sample(String operator, String pattern, @Nullable Set<String> expected) {

        static Sample ilike(final String pattern, final int... words) {
            return new Sample("ilike", pattern, thingIds(words));
        }

        static Sample like(final String pattern, final int... words) {
            return new Sample("like", pattern, thingIds(words));
        }

        static Sample ilikeUnpinned(final String pattern) {
            return new Sample("ilike", pattern, null);
        }

        Predicate predicate() {
            return "ilike".equals(operator) ? CF.ilike(pattern) : CF.like(pattern);
        }

        String indexOperator() {
            return "ilike".equals(operator) ? "~~*" : "~~";
        }
    }

}
