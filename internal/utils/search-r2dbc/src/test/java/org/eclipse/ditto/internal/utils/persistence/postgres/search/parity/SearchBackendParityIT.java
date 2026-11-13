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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.test.docker.mongo.MongoDbResource;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.parser.thingsearch.RqlOptionParser;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.thingsearch.api.query.filter.ParameterOptionVisitor;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultList;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.thingsearch.service.starter.actors.ParityCursorWalker;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * <strong>The Mongo &lt;-&gt; Postgres parity matrix</strong> (plan Phase G, the single most important test of the
 * pluggable-search effort): ONE corpus of things+policies is written to BOTH a real Mongo container and a real
 * PG16 container through their respective FULL production write paths (shared neutral enforcement front
 * {@code EnforcedThingMapper}/{@code SearchIndexDocumentFactory}; Mongo: {@code SearchIndexDocumentMongoEncoder} +
 * {@code MongoSearchUpdaterFlow}; Postgres: {@code ThingFlattener} + {@code PostgresSearchUpdaterFlow}), then a
 * data-driven battery of RQL queries (every operator x path kind, crossed with every auth situation, sorts, counts
 * and real-{@code ThingsSearchCursor} walks to exhaustion) is parsed through the real RQL front door and executed
 * against both backends; result ID sets — and order, where the sort is total — must be identical.
 *
 * <p>Divergences are allowed ONLY where frozen in {@code parity/divergence-allowlist.json}; every allow-listed
 * case still EXECUTES on both backends and the divergent behavior of each backend is pinned by assertion, never
 * skipped. A divergence outside the allowlist is a production defect in one backend and fails this IT.</p>
 *
 * <p>NO offline-skip guard on purpose: the parity matrix is the correctness backstop of the whole effort, so a
 * missing Docker daemon must FAIL the verification loudly (zero-skip discipline), unlike the module's
 * per-backend ITs.</p>
 *
 * <p><strong>Findings baked into the battery design</strong> (established empirically while building this IT):</p>
 * <ul>
 *     <li><strong>Shared upstream cursor limitations (IDENTICAL on both backends, parity-preserving):</strong>
 *     (a) the cursor's gt/lt resume predicates are type-bracketed, so a paged walk cannot cross a
 *     number-&gt;string-&gt;boolean sort-type boundary on EITHER backend (the same limitation
 *     {@code PostgresCursorResumeIT} documents — hence the walks cross numeric SUB-type boundaries int/long/double);
 *     (b) a thing with an EXPLICIT null (or empty-array) sort value re-matches the ASC null-boundary resume filter
 *     ({@code exists(key)} is true for an explicit null and an empty array folds into the null sort group on both
 *     backends), duplicating or live-locking the walk identically on both backends. Explicit-null/empty-array sort
 *     positions are therefore covered by the SORT battery (strict order parity) but excluded from walk corpora.</li>
 *     <li><strong>Allowlist entry #8 ({@code sudo-wildcard-value-mongo-degenerate-path}, G1 adjudication):</strong>
 *     under SUDO, Mongo's {@code GetFilterBsonVisitor.matchWildcardFeatureValue} degrades to a top-level dotted
 *     path (e.g. {@code properties.status}) that no document has. For an ordinary operand that path matches
 *     NOTHING, while Postgres answers {@code features/*} value predicates semantically — PG is a strict superset.
 *     For a null-including operand ({@code in(…, null)}, {@code ge(null)}, {@code le(null)}; Mongo's null = missing)
 *     the same degenerate path matches EVERY thing and its negation nothing, while Postgres still answers per
 *     visible feature. No auth impact (sudo bypasses auth by definition). The affected wildcard VALUE-predicate
 *     cases run under ALL auth situations: the sudo run PINS each backend's observed page (Mongo's empty result /
 *     zero count or its every-thing page, Postgres' exact semantic hits), every non-sudo situation asserts strict
 *     parity.</li>
 * </ul>
 */
public final class SearchBackendParityIT {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();
    private static final PostgresDbResource POSTGRES_RESOURCE = new PostgresDbResource();

    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    private static final RqlOptionParser OPTION_PARSER = new RqlOptionParser();

    // built from the PRODUCTION simple-field-mappings (SearchConfig default: thingId, namespace, policyId,
    // _revision, _modified, _created, definition, _metadata), exactly like SearchRootActor.getQueryParser — the
    // same neutral criteria front is shared by both backends
    private static ThingsFieldExpressionFactory fef;
    private static QueryFilterCriteriaFactory criteriaFactory;

    private static ActorSystem system;
    private static ParityFixtures fixtures;
    private static ParityBackend mongo;
    private static ParityBackend postgres;

    @BeforeClass
    public static void startBackendsAndWriteCorpus() {
        // deliberately NO Assume guard: no Docker => this IT FAILS (see class javadoc)
        POSTGRES_RESOURCE.start();
        final Config config = ConfigFactory.load("parity/search-parity-test");
        final var searchConfig = org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig.of(
                org.eclipse.ditto.internal.utils.config.DefaultScopedConfig.dittoScoped(config));
        fef = ThingsFieldExpressionFactory.of(searchConfig.getSimpleFieldMappings());
        criteriaFactory = QueryFilterCriteriaFactory.of(fef, RqlPredicateParser.getInstance());
        system = ActorSystem.create("SearchBackendParityIT");
        fixtures = ParityFixtures.load();
        mongo = MongoParityBackend.start(MONGO_RESOURCE, system, config);
        postgres = PostgresParityBackend.start(POSTGRES_RESOURCE, config);
        writeCorpus(mongo);
        writeCorpus(postgres);
    }

    @AfterClass
    public static void stopBackends() {
        if (mongo != null) {
            mongo.close();
        }
        if (postgres != null) {
            postgres.close();
        }
        if (system != null) {
            system.terminate();
        }
        POSTGRES_RESOURCE.stop();
    }

    // ================================================================================================= corpus writes

    private static void writeCorpus(final ParityBackend backend) {
        for (final ThingWriteModel model : fixtures.writeModels()) {
            // initial-write shape: last = delete tombstone, exactly like the production stream's first update
            final var last = ThingDeleteModel.of(Metadata.ofDeleted(model.getMetadata().getThingId()));
            final UpdaterResult result = runSingle(
                    Source.single(new UpdaterData(model, last)).via(backend.updaterFlow().create()),
                    "corpus write of " + model.getMetadata().getThingId() + " on " + backend.name());
            assertThat(result.result().isAcknowledged())
                    .as("acknowledged corpus write of <%s> on <%s>", model.getMetadata().getThingId(), backend.name())
                    .isTrue();
            assertThat(result.result().getErrors())
                    .as("no write errors for <%s> on <%s>", model.getMetadata().getThingId(), backend.name())
                    .isEmpty();
        }
    }

    // ==================================================================================================== the matrix

    @Test
    public void corpusIsFullyVisibleOnBothBackends() {
        final long expected = fixtures.writeModels().size();
        for (final ParityBackend backend : List.of(mongo, postgres)) {
            final Query query = backend.queryBuilderFactory()
                    .newUnlimitedBuilder(criteriaFactory.filterCriteria(null, HEADERS))
                    .build();
            final Long count = runSingle(backend.searchPersistence().sudoCount(query, HEADERS),
                    "sudo count on " + backend.name());
            assertThat(count).as("full corpus sudo count on <%s>", backend.name()).isEqualTo(expected);
        }
    }

    @Test
    public void queryBatteryParityAcrossAuthSituations() {
        final SoftAssertions softly = new SoftAssertions();
        int comparisons = 0;
        int nonEmptySudoCases = 0;
        for (final QueryCase queryCase : fixtures.queryCases()) {
            if (QueryCase.MODE_CURSOR_WALK.equals(queryCase.mode())) {
                continue; // walked by cursorWalksPageToExhaustionOnBothBackends
            }
            for (final String authKey : authKeysFor(queryCase)) {
                runBatteryComparison(softly, queryCase, authKey);
                comparisons++;
            }
            if (queryCase.expectNonEmptySudo()) {
                nonEmptySudoCases++;
            }
        }
        softly.assertAll();
        assertThat(comparisons).as("battery executed a real matrix").isGreaterThan(100);
        assertThat(nonEmptySudoCases).as("anti-vacuousness: enough cases assert non-empty sudo results")
                .isGreaterThan(20);
    }

    private static void runBatteryComparison(final SoftAssertions softly, final QueryCase queryCase,
            final String authKey) {

        final List<String> subjects = subjectsFor(authKey);
        final String description = "case <" + queryCase.name() + "> auth <" + authKey + ">";

        if (queryCase.expectPostgresRejection()) {
            assertPostgresRejectionDivergence(softly, queryCase, subjects, description);
            return;
        }

        if (QueryCase.MODE_COUNT.equals(queryCase.mode())) {
            final long mongoCount = count(mongo, queryCase, subjects, description);
            final long postgresCount = count(postgres, queryCase, subjects, description);
            final Long mongoCountPin = queryCase.expectedCountMongo().get(authKey);
            final Long postgresCountPin = queryCase.expectedCountPostgres().get(authKey);
            if (mongoCountPin != null || postgresCountPin != null) {
                // allow-listed count divergence: pin BOTH backends' observed counts explicitly
                softly.assertThat(mongoCountPin).as("%s: count pin must cover mongo", description).isNotNull();
                softly.assertThat(postgresCountPin).as("%s: count pin must cover postgres", description)
                        .isNotNull();
                if (mongoCountPin != null) {
                    softly.assertThat(mongoCount).as("%s: pinned mongo count (allowlist <%s>)", description,
                            queryCase.divergenceId()).isEqualTo(mongoCountPin);
                }
                if (postgresCountPin != null) {
                    softly.assertThat(postgresCount).as("%s: pinned postgres count (allowlist <%s>)", description,
                            queryCase.divergenceId()).isEqualTo(postgresCountPin);
                }
            } else {
                softly.assertThat(postgresCount).as("%s: count parity", description).isEqualTo(mongoCount);
            }
            if (queryCase.expectNonEmptySudo() && subjects == null) {
                softly.assertThat(mongoCount).as("%s: non-empty sudo count", description).isGreaterThan(0L);
            }
            if (authKey.equals(queryCase.expectNonEmptyAuth())) {
                softly.assertThat(mongoCount).as("%s: non-empty count under <%s>", description, authKey)
                        .isGreaterThan(0L);
            }
            return;
        }

        final List<String> mongoIds = findIds(mongo, queryCase, subjects, description);
        final List<String> postgresIds = findIds(postgres, queryCase, subjects, description);

        final List<String> mongoPin = queryCase.expectedMongo().get(authKey);
        final List<String> postgresPin = queryCase.expectedPostgres().get(authKey);
        if (mongoPin != null || postgresPin != null) {
            // allow-listed divergence: pin BOTH backends' observed behavior explicitly
            softly.assertThat(mongoPin).as("%s: divergence pin must cover mongo", description).isNotNull();
            softly.assertThat(postgresPin).as("%s: divergence pin must cover postgres", description).isNotNull();
            if (mongoPin != null) {
                softly.assertThat(mongoIds).as("%s: pinned mongo result (allowlist <%s>)", description,
                        queryCase.divergenceId()).isEqualTo(mongoPin);
            }
            if (postgresPin != null) {
                softly.assertThat(postgresIds).as("%s: pinned postgres result (allowlist <%s>)", description,
                        queryCase.divergenceId()).isEqualTo(postgresPin);
            }
        } else if (queryCase.compareAsSet()) {
            softly.assertThat(new LinkedHashSet<>(postgresIds))
                    .as("%s: result-set parity (unordered)", description)
                    .isEqualTo(new LinkedHashSet<>(mongoIds));
        } else {
            softly.assertThat(postgresIds).as("%s: result parity (ordered)", description).isEqualTo(mongoIds);
        }

        if (queryCase.expectNonEmptySudo() && subjects == null) {
            softly.assertThat(mongoIds).as("%s: non-empty sudo result", description).isNotEmpty();
        }
        if (authKey.equals(queryCase.expectNonEmptyAuth())) {
            softly.assertThat(mongoIds).as("%s: non-empty result under <%s>", description, authKey).isNotEmpty();
        }
    }

    private static void assertPostgresRejectionDivergence(final SoftAssertions softly, final QueryCase queryCase,
            @Nullable final List<String> subjects, final String description) {

        // Mongo answers (a silent never-matching '*' path => unordered by that key), Postgres hard-rejects.
        final List<String> mongoIds = findIds(mongo, queryCase, subjects, description);
        final List<String> mongoPin = queryCase.expectedMongo().get(authKeyOf(subjects));
        if (mongoPin != null) {
            softly.assertThat(new LinkedHashSet<>(mongoIds))
                    .as("%s: mongo still answers the wildcard-sorted query (allowlist <%s>)", description,
                            queryCase.divergenceId())
                    .isEqualTo(new LinkedHashSet<>(mongoPin));
        }
        final Throwable postgresRejection = catchThrowable(() -> findIds(postgres, queryCase, subjects, description));
        softly.assertThat(postgresRejection)
                .as("%s: postgres hard-rejects the wildcard sort key (allowlist <%s>)", description,
                        queryCase.divergenceId())
                .isNotNull();
        if (postgresRejection != null) {
            softly.assertThat(hasIllegalArgumentCause(postgresRejection))
                    .as("%s: postgres rejection is an IllegalArgumentException (was: %s)", description,
                            postgresRejection)
                    .isTrue();
        }
    }

    private static boolean hasIllegalArgumentCause(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalArgumentException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // ================================================================================================== cursor walks

    @Test
    public void cursorWalksPageToExhaustionOnBothBackends() {
        final SoftAssertions softly = new SoftAssertions();
        int walks = 0;
        for (final QueryCase queryCase : fixtures.queryCases()) {
            if (!QueryCase.MODE_CURSOR_WALK.equals(queryCase.mode())) {
                continue;
            }
            for (final String authKey : authKeysFor(queryCase)) {
                runCursorWalkComparison(softly, queryCase, authKey);
                walks++;
            }
        }
        softly.assertAll();
        assertThat(walks).as("cursor walks executed").isGreaterThanOrEqualTo(3);
    }

    private static void runCursorWalkComparison(final SoftAssertions softly, final QueryCase queryCase,
            final String authKey) {

        final List<String> subjects = subjectsFor(authKey);
        final String description = "cursor walk <" + queryCase.name() + "> auth <" + authKey + ">";
        final SortOption modelSort = modelSortOptionOf(queryCase);

        final List<List<String>> mongoPages = walk(mongo, queryCase, modelSort, subjects, description);
        final List<List<String>> postgresPages = walk(postgres, queryCase, modelSort, subjects, description);

        final List<String> mongoPin = queryCase.expectedMongo().get(authKey);
        final List<String> postgresPin = queryCase.expectedPostgres().get(authKey);
        if (mongoPin != null || postgresPin != null) {
            // allow-listed divergent walk: the container-valued boundaries carry divergent cursor sort-value
            // encodings (Mongo: whole array/object; Postgres: null) AND the shared type-bracketed gt/lt resume
            // predicates make each backend drop a DIFFERENT subset mid-walk — pin each backend's own observed
            // sequence. The unpaged-reference invariant deliberately does NOT apply here (the pins capture the
            // dropped things), while both walks must still TERMINATE.
            softly.assertThat(flatten(mongoPages)).as("%s: pinned mongo walk (allowlist <%s>)", description,
                    queryCase.divergenceId()).isEqualTo(mongoPin);
            softly.assertThat(flatten(postgresPages)).as("%s: pinned postgres walk (allowlist <%s>)", description,
                    queryCase.divergenceId()).isEqualTo(postgresPin);
            return;
        }

        softly.assertThat(postgresPages)
                .as("%s: page-by-page parity (page boundaries included)", description)
                .isEqualTo(mongoPages);
        softly.assertThat(flatten(mongoPages)).as("%s: walk returned things", description).isNotEmpty();

        // walk-level invariant on BOTH backends: concatenated pages equal the unpaged reference — no drops, no dupes
        for (final ParityBackend backend : List.of(mongo, postgres)) {
            final List<List<String>> pages = backend == mongo ? mongoPages : postgresPages;
            final List<String> unpaged = findIds(backend, unpagedVariantOf(queryCase), subjects, description);
            softly.assertThat(flatten(pages))
                    .as("%s: paged walk equals unpaged reference on <%s>", description, backend.name())
                    .isEqualTo(unpaged);
        }
    }

    private static List<List<String>> walk(final ParityBackend backend, final QueryCase queryCase,
            final SortOption modelSort, @Nullable final List<String> subjects, final String description) {
        return ParityCursorWalker.walk(
                () -> buildQuery(backend, queryCase),
                modelSort,
                query -> runSingle(backend.searchPersistence().findAll(query, subjects, null, HEADERS),
                        description + " page on " + backend.name()),
                system);
    }

    /** The same case without its {@code size(...)} page-limit option: the unpaged reference of a cursor walk. */
    private static QueryCase unpagedVariantOf(final QueryCase queryCase) {
        final List<String> unpagedOptions = queryCase.options().stream()
                .filter(option -> !option.startsWith("size("))
                .toList();
        return new QueryCase(queryCase.name() + "-unpaged", queryCase.filter(), unpagedOptions,
                queryCase.namespaces(), QueryCase.MODE_FIND, queryCase.authKeys(), false, null, Map.of(), Map.of(),
                Map.of(), Map.of(), false, false, null);
    }

    private static SortOption modelSortOptionOf(final QueryCase queryCase) {
        return queryCase.options().stream()
                .filter(option -> option.startsWith("sort("))
                .flatMap(option -> OPTION_PARSER.parse(option).stream())
                .filter(SortOption.class::isInstance)
                .map(SortOption.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "cursorWalk case <" + queryCase.name() + "> needs an explicit sort(...) option"));
    }

    // ============================================================================================ allowlist closure

    @Test
    public void divergenceAllowlistIsFullyExercisedAndClosed() {
        final Set<String> declared = fixtures.allowlist().keySet();
        final Set<String> referenced = new LinkedHashSet<>();
        for (final QueryCase queryCase : fixtures.queryCases()) {
            if (queryCase.divergenceId() != null) {
                referenced.add(queryCase.divergenceId());
                assertThat(queryCase.pinsDivergentBehavior())
                        .as("allow-listed case <%s> must PIN the divergent behavior (expected.mongo/postgres, "
                                + "expectedCount.mongo/postgres or expectPostgresRejection), not just reference "
                                + "the allowlist", queryCase.name())
                        .isTrue();
            } else {
                assertThat(queryCase.pinsDivergentBehavior())
                        .as("case <%s> pins per-backend results but declares no allowlist id — widening the "
                                + "allowlist implicitly is forbidden", queryCase.name())
                        .isFalse();
            }
        }
        assertThat(referenced)
                .as("every frozen allowlist entry is exercised by at least one case, and no case references an "
                        + "undeclared divergence")
                .isEqualTo(declared);
    }

    // ============================================================================================== query execution

    private static List<String> authKeysFor(final QueryCase queryCase) {
        if (queryCase.authKeys().isEmpty()) {
            return List.copyOf(fixtures.authSituations().keySet());
        }
        for (final String authKey : queryCase.authKeys()) {
            if (!fixtures.authSituations().containsKey(authKey)) {
                throw new IllegalStateException(
                        "case <" + queryCase.name() + "> references unknown auth situation <" + authKey + ">");
            }
        }
        return queryCase.authKeys();
    }

    @Nullable
    private static List<String> subjectsFor(final String authKey) {
        return fixtures.authSituations().get(authKey);
    }

    @Nullable
    private static String authKeyOf(@Nullable final List<String> subjects) {
        return fixtures.authSituations().entrySet().stream()
                .filter(entry -> subjects == null ? entry.getValue() == null : subjects.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static Query buildQuery(final ParityBackend backend, final QueryCase queryCase) {
        final Criteria criteria = parseCriteria(queryCase);
        final QueryBuilder builder = QueryCase.MODE_COUNT.equals(queryCase.mode())
                ? backend.queryBuilderFactory().newUnlimitedBuilder(criteria)
                : backend.queryBuilderFactory().newBuilder(criteria);
        if (!queryCase.options().isEmpty()) {
            new ParameterOptionVisitor(fef, builder)
                    .visitAll(OPTION_PARSER.parse(String.join(",", queryCase.options())));
        }
        return builder.build();
    }

    private static Criteria parseCriteria(final QueryCase queryCase) {
        if (queryCase.namespaces() != null) {
            return criteriaFactory.filterCriteriaRestrictedByNamespaces(queryCase.filter(), HEADERS,
                    queryCase.namespaces());
        }
        return criteriaFactory.filterCriteria(queryCase.filter(), HEADERS);
    }

    private static List<String> findIds(final ParityBackend backend, final QueryCase queryCase,
            @Nullable final List<String> subjects, final String description) {
        final Query query = buildQuery(backend, queryCase);
        final ResultList<TimestampedThingId> page = runSingle(
                backend.searchPersistence().findAll(query, subjects, null, HEADERS),
                description + " findAll on " + backend.name());
        final List<String> ids = new ArrayList<>();
        page.forEach(entry -> ids.add(entry.thingId().toString()));
        return ids;
    }

    private static long count(final ParityBackend backend, final QueryCase queryCase,
            @Nullable final List<String> subjects, final String description) {
        final Query query = buildQuery(backend, queryCase);
        final Source<Long, ?> source = subjects == null
                ? backend.searchPersistence().sudoCount(query, HEADERS)
                : backend.searchPersistence().count(query, subjects, HEADERS);
        return runSingle(source, description + " count on " + backend.name());
    }

    private static List<String> flatten(final List<List<String>> pages) {
        final List<String> all = new ArrayList<>();
        pages.forEach(all::addAll);
        return all;
    }

    private static <T> T runSingle(final Source<T, ?> source, final String description) {
        try {
            return source.runWith(Sink.head(), system).toCompletableFuture().get(60, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(description + " interrupted", e);
        } catch (final Exception e) {
            throw new IllegalStateException(description + " failed", e);
        }
    }
}
