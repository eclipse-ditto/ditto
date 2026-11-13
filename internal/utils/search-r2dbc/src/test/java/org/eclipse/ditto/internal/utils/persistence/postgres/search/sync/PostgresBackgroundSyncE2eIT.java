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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.base.model.entity.Revision;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresQuery;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.read.PostgresThingsSearchPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresSearchUpdaterFlow;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyRevision;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyRevisionResponse;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultList;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResultStatus;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.BackgroundSyncStream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Background-sync end-to-end IT against the REAL Postgres search backend (plan Phase G, bullet 2): six scenarios
 * proving that the production sync machinery detects and repairs index inconsistencies on a real PG 16 container.
 * <p>
 * The pipeline under test is the production one — the REAL {@link BackgroundSyncStream} (thingsearch/service MAIN
 * class) comparing a things-side {@code Source<Metadata>} against the REAL
 * {@link PostgresThingsSearchPersistence#sudoStreamMetadata} stream over rows written by the REAL
 * {@link PostgresSearchUpdaterFlow}; flagged things are re-indexed through that same real flow and the repair is
 * verified by real RQL query answers ({@link PostgresThingsSearchPersistence#findAll}). Only two seams are
 * fixture/stub by design (the genuinely-both-services-on-PG verification is G3's full-stack run):
 * </p>
 * <ul>
 *     <li>the things-side metadata source is fixture-built, shaped EXACTLY like
 *     {@code ThingsMetadataSource.toMetadata} output: thing revision from the snapshot, policy tag with revision 0
 *     (the policy revision is not known from a thing snapshot), NO referenced policy tags, modified timestamp from
 *     the snapshot;</li>
 *     <li>the policies shard region is a minimal auto-replying stub actor answering {@code SudoRetrievePolicy} /
 *     {@code SudoRetrievePolicyRevision} from a per-scenario policy table (precedent:
 *     {@code BackgroundSyncStreamTest}'s TestKit probe — replicated here because that module's test classes are not
 *     a dependency).</li>
 * </ul>
 * <p>
 * Scenarios (each also pins the NEGATIVE — consistent things must emit NOTHING from the sync stream — and, where
 * meaningful, that a second sync pass after the repair emits nothing, i.e. the repair converges):
 * </p>
 * <ol>
 *     <li>missing-from-index → flagged → re-indexed → RQL query finds it;</li>
 *     <li>out-of-date thing revision → flagged with thing-cache invalidation → re-indexed → query reflects the new
 *     state; the up-to-date companion stays silent;</li>
 *     <li><strong>policy-update fan-out with UNCHANGED thing revision</strong> (the round-2 Critical regression):
 *     identical thing revision on both sides, bumped policy revision → MUST be flagged, and the same-revision repair
 *     write MUST land via the unconditional upsert (the old {@code revision < EXCLUDED.revision} guard starved
 *     exactly this write) → auth visibility flips;</li>
 *     <li>imported-policy staleness: detected from the FULL referenced policy tags (id+revision) that
 *     {@code sudoStreamMetadata} rebuilds from the {@code referenced_policies} column (C2/C3 adjudication);</li>
 *     <li>force-update: a bit-wrong but revision-equal row is INVISIBLE to the normal comparison (pinned) and is
 *     rewritten by the force route's unconditional upsert;</li>
 *     <li>tolerance window: index rows modified inside the window are skipped on both index-side branches; zero
 *     window flags them all.</li>
 * </ol>
 * <p>
 * NOTE (scenarios 3 &amp; 4): the REPAIRED write model these scenarios re-index is hand-authored in the test (the
 * post-fan-out / post-import auth state is supplied directly), not recomputed here by the real enforcement flow — the
 * production enforcement recompute that produces that write model from a policy change is covered end-to-end by the G3
 * full-stack run. What these scenarios prove is the DETECTION path (real {@code BackgroundSyncStream} ×
 * {@code sudoStreamMetadata}) and that the repair write lands via the unconditional upsert.
 * </p>
 * <p>
 * Deliberately NO offline-skip guard (module Phase-G zero-skip discipline, precedent {@code SearchBackendParityIT}):
 * a missing Docker daemon must FAIL this verification loudly.
 * </p>
 */
public final class PostgresBackgroundSyncE2eIT {

    private static final String NS = "org.eclipse.ditto";
    private static final String GRANT_KEY = "·g";
    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    /** Well outside the tolerance window — entries modified "recently" are ignored by the sync stream. */
    private static final Instant OLD_MODIFIED = Instant.parse("2020-01-01T00:00:00Z");
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static DittoPostgresClient client;
    private static ConnectionFactory connectionFactory;
    private static ActorSystem system;

    private final ThingsFieldExpressionFactory fef =
            ThingsFieldExpressionFactory.of(Map.of("thingId", "_id", "namespace", "_namespace"));
    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    private PostgresSearchUpdaterFlow flow;
    private PostgresThingsSearchPersistence persistence;

    @BeforeClass
    public static void startContainer() {
        // deliberately NO Assume guard: no Docker => this IT FAILS (see class javadoc)
        POSTGRES.start();
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.initial-size = 1\n"
                        + "ditto.postgresql.pool.max-size = 4\n"
                        + "ditto.postgresql.force-custom-plan = true\n");
        client = DittoPostgresClient.newInstance(DefaultPostgresConfig.of(config.getConfig("ditto")));
        connectionFactory = client.getConnectionPool();
        system = ActorSystem.create("PostgresBackgroundSyncE2eIT");
    }

    @AfterClass
    public static void stopContainer() {
        if (system != null) {
            system.terminate();
        }
        if (client != null) {
            client.close();
        }
        POSTGRES.stop();
    }

    @Before
    public void bootstrap() {
        runSql("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        flow = PostgresSearchUpdaterFlow.of(client);
        persistence = PostgresThingsSearchPersistence.of(client);
    }

    @After
    public void clear() {
        runSql("TRUNCATE search_flat, search_things, search_sync");
    }

    // ================================================================================================================
    // scenario 1 — missing-from-index
    // ================================================================================================================

    @Test
    public void missingFromIndexIsFlaggedReindexedAndFoundByQuery() {
        final PolicyId policy = PolicyId.of(NS, "sync-policy-1");
        final ThingId missing = ThingId.of(NS, "sync-a-missing");
        final ThingId consistent = ThingId.of(NS, "sync-b-consistent");

        // index side: ONLY the consistent thing exists (written through the real updater flow).
        index(doc(consistent, 1L, policy, 1L, refs(policy, 1L), "blue", List.of("s1")));
        // things side: BOTH things exist.
        final List<Metadata> thingsSide = List.of(
                thingsSideMetadata(missing, 1L, policy),
                thingsSideMetadata(consistent, 1L, policy));
        final Map<PolicyId, Policy> policies = policies(policyOf(policy, 1L));

        assertThat(sudoFind(colorEq("red"))).as("missing thing must not be queryable before repair").isEmpty();

        final List<Metadata> emissions = runSync(policies, thingsSide);

        // exactly the missing thing is flagged (PersistedAndNotIndexed, confirmed via the policies stub);
        // the consistent companion emits NOTHING (negative pin).
        assertThat(thingIds(emissions)).containsExactly(missing.toString());
        assertThat(emissions.get(0).getThingRevision()).isEqualTo(1L);

        reindexEmissions(emissions, Map.of(missing,
                writeModel(doc(missing, 1L, policy, 1L, refs(policy, 1L), "red", List.of("s1")))));

        assertThat(authFind(colorEq("red"), List.of("s1"))).containsExactly(missing.toString());
        // repair converges: a second sync pass over the same things-side emits nothing.
        assertThat(runSync(policies, thingsSide)).isEmpty();
    }

    // ================================================================================================================
    // scenario 2 — out-of-date thing revision (and the inverse: up-to-date emits NOTHING)
    // ================================================================================================================

    @Test
    public void outOfDateRevisionIsFlaggedWhileUpToDateEmitsNothing() {
        final PolicyId policy = PolicyId.of(NS, "sync-policy-2");
        final ThingId stale = ThingId.of(NS, "sync-c-stale");
        final ThingId current = ThingId.of(NS, "sync-d-current");

        index(doc(stale, 1L, policy, 1L, refs(policy, 1L), "red", List.of("s1")));
        index(doc(current, 3L, policy, 1L, refs(policy, 1L), "green", List.of("s1")));

        final List<Metadata> thingsSide = List.of(
                thingsSideMetadata(stale, 2L, policy),   // things side moved on to revision 2
                thingsSideMetadata(current, 3L, policy)); // index is up to date -> MUST stay silent
        final Map<PolicyId, Policy> policies = policies(policyOf(policy, 1L));

        final List<Metadata> emissions = runSync(policies, thingsSide);

        // exactly the stale thing; the up-to-date companion emits NOTHING (the mandated inverse pin).
        assertThat(thingIds(emissions)).containsExactly(stale.toString());
        final Metadata emission = emissions.get(0);
        // the RevisionMismatch branch emits the INDEXED entry flagged for thing-cache invalidation.
        assertThat(emission.getThingRevision()).isEqualTo(1L);
        assertThat(emission.shouldInvalidateThing()).isTrue();
        assertThat(emission.shouldInvalidatePolicy()).isFalse();

        reindexEmissions(emissions, Map.of(stale,
                writeModel(doc(stale, 2L, policy, 1L, refs(policy, 1L), "blue", List.of("s1")))));

        // the query reflects the new state — old state gone, companion untouched.
        assertThat(sudoFind(colorEq("blue"))).containsExactly(stale.toString());
        assertThat(sudoFind(colorEq("red"))).isEmpty();
        assertThat(sudoFind(colorEq("green"))).containsExactly(current.toString());
        assertThat(runSync(policies, thingsSide)).isEmpty();
    }

    // ================================================================================================================
    // scenario 3 — policy-update fan-out with UNCHANGED thing revision (round-2 Critical regression)
    // ================================================================================================================

    @Test
    public void policyFanoutWithUnchangedThingRevisionIsFlaggedAndVisibilityFlips() {
        final PolicyId fanout = PolicyId.of(NS, "sync-policy-fanout");
        final PolicyId steady = PolicyId.of(NS, "sync-policy-steady");
        final ThingId thing = ThingId.of(NS, "sync-e-fanout");
        final ThingId bystander = ThingId.of(NS, "sync-f-steady");

        index(doc(thing, 5L, fanout, 1L, refs(fanout, 1L), "red", List.of("subject-old")));
        index(doc(bystander, 1L, steady, 1L, refs(steady, 1L), "red", List.of("subject-old")));

        // BEFORE: only the old subject sees the thing.
        assertThat(authFind(colorEq("red"), List.of("subject-old")))
                .containsExactly(thing.toString(), bystander.toString());
        assertThat(authFind(colorEq("red"), List.of("subject-new"))).isEmpty();

        // things side: SAME thing revisions; only the fan-out policy moved to revision 2 on the policies side.
        final List<Metadata> thingsSide = List.of(
                thingsSideMetadata(thing, 5L, fanout),
                thingsSideMetadata(bystander, 1L, steady));
        final Map<PolicyId, Policy> policies = policies(policyOf(fanout, 2L), policyOf(steady, 1L));

        final List<Metadata> emissions = runSync(policies, thingsSide);

        // ROUND-2 CRITICAL REGRESSION: thing revision identical on both sides — the sync MUST still flag it
        // (policy revision 1 in the index vs 2 at the policies service); the bystander emits NOTHING.
        assertThat(thingIds(emissions)).containsExactly(thing.toString());
        final Metadata emission = emissions.get(0);
        assertThat(emission.getThingRevision()).isEqualTo(5L);
        assertThat(emission.shouldInvalidatePolicy()).isTrue();
        assertThat(emission.shouldInvalidateThing()).isFalse();

        // ... and the repair write — SAME thing revision 5, new policy enforcement — MUST land via the
        // unconditional upsert (the old `revision < EXCLUDED.revision` guard would have starved exactly this).
        reindexEmissions(emissions, Map.of(thing,
                writeModel(doc(thing, 5L, fanout, 2L, refs(fanout, 2L), "red", List.of("subject-new")))));

        // AFTER: visibility flipped — the query whose answer depends on the policy change answers differently.
        assertThat(authFind(colorEq("red"), List.of("subject-new"))).containsExactly(thing.toString());
        assertThat(authFind(colorEq("red"), List.of("subject-old"))).containsExactly(bystander.toString());
        assertThat(runSync(policies, thingsSide)).isEmpty();
    }

    // ================================================================================================================
    // scenario 4 — imported-policy staleness (referenced_policies in the metadata stream)
    // ================================================================================================================

    @Test
    public void importedPolicyStalenessIsDetectedFromReferencedPolicyTags() {
        final PolicyId owner = PolicyId.of(NS, "sync-policy-owner-stale");
        final PolicyId imported = PolicyId.of(NS, "sync-policy-imported-stale");
        final PolicyId ownerFresh = PolicyId.of(NS, "sync-policy-owner-fresh");
        final PolicyId importedFresh = PolicyId.of(NS, "sync-policy-imported-fresh");
        final ThingId stale = ThingId.of(NS, "sync-g-import-stale");
        final ThingId fresh = ThingId.of(NS, "sync-h-import-fresh");

        index(doc(stale, 2L, owner, 3L, Set.of(PolicyTag.of(owner, 3L), PolicyTag.of(imported, 1L)),
                "purple", List.of("subject-imp-old")));
        index(doc(fresh, 2L, ownerFresh, 3L, Set.of(PolicyTag.of(ownerFresh, 3L), PolicyTag.of(importedFresh, 1L)),
                "purple", List.of("subject-imp-old")));

        // C2/C3 adjudication: the REAL search-side stream exposes FULL referenced policy tags (id AND revision).
        final Metadata indexedStale = streamIndex().stream()
                .filter(metadata -> metadata.getThingId().equals(stale)).findFirst().orElseThrow();
        assertThat(indexedStale.getThingPolicyTag()).contains(PolicyTag.of(owner, 3L));
        assertThat(indexedStale.getAllReferencedPolicyTags())
                .containsExactlyInAnyOrder(PolicyTag.of(owner, 3L), PolicyTag.of(imported, 1L));

        final List<Metadata> thingsSide = List.of(
                thingsSideMetadata(stale, 2L, owner),
                thingsSideMetadata(fresh, 2L, ownerFresh));
        // policies side: both owner policies (unchanged, revision 3) import their template policy;
        // the STALE thing's imported policy moved to revision 2, the fresh one still matches the index.
        final Map<PolicyId, Policy> policies = policies(
                importingPolicy(owner, 3L, imported),
                policyOf(imported, 2L), // bumped
                importingPolicy(ownerFresh, 3L, importedFresh),
                policyOf(importedFresh, 1L)); // matches the indexed referenced tag

        final List<Metadata> emissions = runSync(policies, thingsSide);

        // BackgroundSyncStream detected the staleness purely from the referenced tag's revision;
        // the thing with the up-to-date import emits NOTHING (negative pin).
        assertThat(thingIds(emissions)).containsExactly(stale.toString());
        assertThat(emissions.get(0).shouldInvalidatePolicy()).isTrue();
        assertThat(emissions.get(0).shouldInvalidateThing()).isFalse();

        // re-index with the enforcement recomputed under the bumped import (its grants moved to a new subject).
        reindexEmissions(emissions, Map.of(stale,
                writeModel(doc(stale, 2L, owner, 3L, Set.of(PolicyTag.of(owner, 3L), PolicyTag.of(imported, 2L)),
                        "purple", List.of("subject-imp-new")))));

        assertThat(authFind(colorEq("purple"), List.of("subject-imp-new"))).containsExactly(stale.toString());
        assertThat(authFind(colorEq("purple"), List.of("subject-imp-old"))).containsExactly(fresh.toString());
        assertThat(runSync(policies, thingsSide)).isEmpty();
    }

    // ================================================================================================================
    // scenario 5 — force-update: bit-wrong but revision-equal
    // ================================================================================================================

    @Test
    public void forceUpdateRewritesBitWrongRevisionEqualRow() {
        final PolicyId policy = PolicyId.of(NS, "sync-policy-force");
        final ThingId thing = ThingId.of(NS, "sync-i-corrupt");

        final ThingWriteModel truth = writeModel(doc(thing, 4L, policy, 1L, refs(policy, 1L), "red", List.of("s1")));
        write(truth);

        // simulate bit-rot: SAME revision, corrupted content in BOTH the doc row and the flat row.
        runSql("UPDATE search_things SET thing = jsonb_set(thing, '{attributes,color}', '\"corrupted\"'::jsonb)"
                + " WHERE thing_id = '" + thing + "'");
        runSql("UPDATE search_flat SET val_text = 'corrupted'"
                + " WHERE thing_id = '" + thing + "' AND path = '/attributes/color'");
        assertThat(sudoFind(colorEq("red"))).isEmpty();
        assertThat(sudoFind(colorEq("corrupted"))).containsExactly(thing.toString());

        // NEGATIVE PIN: the normal sync comparison CANNOT see revision-equal corruption — it emits NOTHING
        // (which is exactly why the force-update route exists).
        final List<Metadata> thingsSide = List.of(thingsSideMetadata(thing, 4L, policy));
        final Map<PolicyId, Policy> policies = policies(policyOf(policy, 1L));
        assertThat(runSync(policies, thingsSide)).isEmpty();

        // FORCE-UPDATE route, driven below the actor layer at the exact production seam:
        // BackgroundSyncActor.streamMetadataFromLowerBound BYPASSES filterForInconsistencies entirely when
        // forceUpdateThings is set (it returns the raw persisted-metadata source), and handleInconsistency turns
        // EVERY streamed thing into SudoUpdateThing(invalidateThing, invalidatePolicy, BACKGROUND_SYNC); the
        // ThingUpdater then recomputes the full write model and pushes it through the updater flow, whose
        // UNCONDITIONAL upsert rewrites the revision-equal row. Here: the same unfiltered persisted stream, each
        // element mapped to the recomputed write model and applied through the REAL PostgresSearchUpdaterFlow.
        final List<Metadata> forced = run(Source.from(thingsSide)); // force mode: NO comparison, everything emitted
        assertThat(thingIds(forced)).containsExactly(thing.toString());
        reindexEmissions(forced, Map.of(thing, truth));

        // the corrupted row was rewritten in place: same revision, corrected content, query answers flip back.
        assertThat(sudoFind(colorEq("red"))).containsExactly(thing.toString());
        assertThat(sudoFind(colorEq("corrupted"))).isEmpty();
        assertThat(scalar("SELECT revision FROM search_things WHERE thing_id = '" + thing + "'")).isEqualTo("4");
        assertThat(scalar("SELECT thing->'attributes'->>'color' FROM search_things WHERE thing_id = '" + thing + "'"))
                .isEqualTo("red");
        // and the repaired row is consistent for the normal sync again.
        assertThat(runSync(policies, thingsSide)).isEmpty();
    }

    // ================================================================================================================
    // scenario 6 — tolerance window on the Postgres-provided t_modified
    // ================================================================================================================

    /**
     * {@code background-sync.tolerance-window} is applied by the shared {@link BackgroundSyncStream} to
     * {@code Metadata.getModified()} of the INDEXED entry. On Postgres that value is {@code search_things.t_modified},
     * written by {@link PostgresSearchUpdaterFlow} from the thing's {@code _modified} and rebuilt by
     * {@code sudoStreamMetadata} — this scenario proves the round trip feeds the window check on the two gates that
     * read the index-side timestamp — the equal-IDs gate (revision / policy-id / policies mismatches) and the
     * indexed-but-not-persisted gate; the persisted-but-not-indexed gate reads the things-side snapshot timestamp and
     * is backend-independent by construction, hence not exercised here.
     */
    @Test
    public void indexEntriesModifiedInsideTheToleranceWindowAreSkippedOnPostgres() {
        final PolicyId policy = PolicyId.of(NS, "sync-policy-tolerance");
        final ThingId recentStale = ThingId.of(NS, "sync-j-recent-stale");
        final ThingId oldStale = ThingId.of(NS, "sync-k-old-stale");
        final ThingId recentOrphan = ThingId.of(NS, "sync-l-recent-orphan");
        final Instant justNow = Instant.now();

        // index side: all three rows at revision 1; ONLY t_modified differs (just now vs. 2020).
        index(doc(recentStale, 1L, policy, 1L, refs(policy, 1L), "red", List.of("s1"), justNow));
        index(doc(oldStale, 1L, policy, 1L, refs(policy, 1L), "red", List.of("s1"), OLD_MODIFIED));
        index(doc(recentOrphan, 1L, policy, 1L, refs(policy, 1L), "red", List.of("s1"), justNow));
        // things side: the two "stale" things moved on to revision 2; the orphan does not exist there at all.
        final List<Metadata> thingsSide = List.of(
                thingsSideMetadata(recentStale, 2L, policy),
                thingsSideMetadata(oldStale, 2L, policy));
        final Map<PolicyId, Policy> policies = policies(policyOf(policy, 1L));

        // 5-minute window: the rows modified just now are left alone (the equal-IDs gate and the
        // indexed-but-not-persisted gate both consult the index-side t_modified); the 2020 row is flagged as usual.
        assertThat(thingIds(runSync(policies, thingsSide, TOLERANCE))).containsExactly(oldStale.toString());

        // zero window, same rows: all three are flagged — the window gated them, not the data.
        assertThat(thingIds(runSync(policies, thingsSide, Duration.ZERO)))
                .containsExactly(recentStale.toString(), oldStale.toString(), recentOrphan.toString());
    }

    // ================================================================================================================
    // sync-run plumbing
    // ================================================================================================================

    /**
     * Run the REAL {@link BackgroundSyncStream} comparison: fixture things-side source vs the REAL
     * {@code sudoStreamMetadata} search-side stream, policy lookups answered by the stub policies shard.
     */
    private List<Metadata> runSync(final Map<PolicyId, Policy> policies, final List<Metadata> thingsSide) {
        return runSync(policies, thingsSide, TOLERANCE);
    }

    private List<Metadata> runSync(final Map<PolicyId, Policy> policies, final List<Metadata> thingsSide,
            final Duration toleranceWindow) {
        final ActorRef policiesShardStub = system.actorOf(PoliciesShardStub.props(policies));
        try {
            final BackgroundSyncStream syncStream = BackgroundSyncStream.of(policiesShardStub,
                    Duration.ofSeconds(10), toleranceWindow, 100, Duration.ofSeconds(1),
                    DefaultNamespacePoliciesConfig.of(ConfigFactory.empty()));
            return run(syncStream.filterForInconsistencies(
                    Source.from(thingsSide), persistence.sudoStreamMetadata(fromStart())));
        } finally {
            system.stop(policiesShardStub);
        }
    }

    /**
     * Fixture things-side metadata shaped EXACTLY like {@code ThingsMetadataSource.toMetadata} builds it from a
     * streamed thing snapshot: policy revision 0 (not known from a thing snapshot), no referenced policy tags,
     * modified timestamp from the snapshot.
     */
    private static Metadata thingsSideMetadata(final ThingId thingId, final long thingRevision,
            final PolicyId policyId) {
        return Metadata.of(thingId, thingRevision, PolicyTag.of(policyId, 0L), null, Set.of(), OLD_MODIFIED, null);
    }

    /** Apply the sync stream's emissions through the REAL updater flow, using the given per-thing truth models. */
    private void reindexEmissions(final List<Metadata> emissions, final Map<ThingId, ThingWriteModel> truth) {
        for (final Metadata emission : emissions) {
            final ThingWriteModel model = truth.get(emission.getThingId());
            assertThat(model).as("re-index truth model for %s", emission.getThingId()).isNotNull();
            write(model);
        }
    }

    private List<Metadata> streamIndex() {
        return run(persistence.sudoStreamMetadata(fromStart()));
    }

    private static ThingId fromStart() {
        return ThingId.of(LowerBound.emptyEntityId(ThingConstants.ENTITY_TYPE));
    }

    private static List<String> thingIds(final List<Metadata> metadata) {
        return metadata.stream().map(m -> m.getThingId().toString()).collect(Collectors.toList());
    }

    // ================================================================================================================
    // policies-shard stub (minimal replication of BackgroundSyncStreamTest's probe wiring)
    // ================================================================================================================

    private static Map<PolicyId, Policy> policies(final Policy... policies) {
        final Map<PolicyId, Policy> map = new LinkedHashMap<>();
        for (final Policy policy : policies) {
            map.put(policy.getEntityId().orElseThrow(), policy);
        }
        return map;
    }

    private static Policy policyOf(final PolicyId policyId, final long revision) {
        return Policy.newBuilder(policyId).setRevision(revision).build();
    }

    private static Policy importingPolicy(final PolicyId policyId, final long revision, final PolicyId imported) {
        return Policy.newBuilder(policyId)
                .setRevision(revision)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(imported))
                .build();
    }

    /**
     * Auto-replying policies shard region stand-in: answers {@code SudoRetrievePolicy} and
     * {@code SudoRetrievePolicyRevision} from an immutable policy table, {@code PolicyNotAccessibleException} for
     * unknown policies — the exact message protocol {@link BackgroundSyncStream} speaks.
     */
    private static final class PoliciesShardStub extends AbstractActor {

        private final Map<PolicyId, Policy> policies;

        private PoliciesShardStub(final Map<PolicyId, Policy> policies) {
            this.policies = policies;
        }

        static Props props(final Map<PolicyId, Policy> policies) {
            return Props.create(PoliciesShardStub.class, () -> new PoliciesShardStub(policies));
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(SudoRetrievePolicy.class, command -> {
                        final Policy policy = policies.get(command.getEntityId());
                        if (policy == null) {
                            getSender().tell(PolicyNotAccessibleException.newBuilder(command.getEntityId()).build(),
                                    getSelf());
                        } else {
                            getSender().tell(SudoRetrievePolicyResponse.of(command.getEntityId(), policy,
                                    DittoHeaders.empty()), getSelf());
                        }
                    })
                    .match(SudoRetrievePolicyRevision.class, command -> {
                        final Policy policy = policies.get(command.getEntityId());
                        if (policy == null) {
                            getSender().tell(PolicyNotAccessibleException.newBuilder(command.getEntityId()).build(),
                                    getSelf());
                        } else {
                            final long revision = policy.getRevision().map(Revision::toLong).orElse(0L);
                            getSender().tell(SudoRetrievePolicyRevisionResponse.of(command.getEntityId(), revision,
                                    DittoHeaders.empty()), getSelf());
                        }
                    })
                    .build();
        }
    }

    // ================================================================================================================
    // index write + query helpers (module precedent: PostgresSearchReadIT)
    // ================================================================================================================

    private static Set<PolicyTag> refs(final PolicyId policyId, final long revision) {
        return Set.of(PolicyTag.of(policyId, revision));
    }

    /**
     * A full search-index document: one {@code color} attribute, readable by exactly {@code readSubjects}
     * (both in {@code global_read} and granted at the auth-tree root), modified well outside the tolerance window.
     */
    private static SearchIndexDocument doc(final ThingId thingId, final long revision, final PolicyId policyId,
            final long policyRevision, final Set<PolicyTag> referencedPolicies, final String color,
            final List<String> readSubjects) {
        return doc(thingId, revision, policyId, policyRevision, referencedPolicies, color, readSubjects,
                OLD_MODIFIED);
    }

    /** Same document, with an explicit {@code _modified} (what the Postgres writer stores as {@code t_modified}). */
    private static SearchIndexDocument doc(final ThingId thingId, final long revision, final PolicyId policyId,
            final long policyRevision, final Set<PolicyTag> referencedPolicies, final String color,
            final List<String> readSubjects, final Instant modified) {
        final JsonObjectBuilder grants = JsonObject.newBuilder();
        for (final String subject : readSubjects) {
            grants.set(subject, JsonObject.empty());
        }
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", (int) revision)
                .set("_modified", modified.toString())
                .set("attributes", JsonObject.newBuilder().set("color", color).build())
                .build();
        return SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(revision)
                .policyId(policyId)
                .policyRevision(policyRevision)
                .referencedPolicies(referencedPolicies)
                .globalRead(readSubjects)
                .thing(thing)
                .policyAuth(JsonObject.newBuilder().set(GRANT_KEY, grants.build()).build())
                .build();
    }

    private static ThingWriteModel writeModel(final SearchIndexDocument document) {
        final Metadata metadata = Metadata.of(document.thingId(), document.revision(),
                PolicyTag.of(document.policyId().orElseThrow(), document.policyRevision()), null,
                document.referencedPolicies(), null);
        return ThingWriteModel.of(metadata, document);
    }

    private void index(final SearchIndexDocument document) {
        write(writeModel(document));
    }

    private void write(final AbstractWriteModel model) {
        try {
            final UpdaterResult result = Source.single(new UpdaterData(model, model))
                    .via(flow.create())
                    .runWith(Sink.head(), system)
                    .toCompletableFuture()
                    .get(60, TimeUnit.SECONDS);
            assertThat(result.result().classify()).isEqualTo(SearchWriteResultStatus.OK);
        } catch (final Exception e) {
            throw new AssertionError("index write failed", e);
        }
    }

    private Criteria colorEq(final String value) {
        return cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq(value));
    }

    private List<String> sudoFind(final Criteria criteria) {
        return findIds(criteria, null);
    }

    private List<String> authFind(final Criteria criteria, final List<String> subjects) {
        return findIds(criteria, subjects);
    }

    private List<String> findIds(final Criteria criteria, final List<String> subjects) {
        final PostgresQuery query = new PostgresQuery(criteria,
                List.of(new SortOption(fef.sortByThingId(), SortDirection.ASC)), 100, 0);
        final ResultList<TimestampedThingId> result = runSingle(persistence.findAll(query, subjects, null, HEADERS));
        return result.stream().map(t -> t.thingId().toString()).collect(Collectors.toList());
    }

    // ================================================================================================================
    // low-level plumbing
    // ================================================================================================================

    private <T> T runSingle(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.head(), system).toCompletableFuture().get(60, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed", e);
        }
    }

    private <T> List<T> run(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.seq(), system).toCompletableFuture().get(60, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed", e);
        }
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block();
    }

    private static void runSql(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }

}
