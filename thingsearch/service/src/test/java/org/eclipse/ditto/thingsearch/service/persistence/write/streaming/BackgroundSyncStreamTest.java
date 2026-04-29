/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyRevision;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyRevisionResponse;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link BackgroundSyncStream}.
 */
public final class BackgroundSyncStreamTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void init() {
        actorSystem = ActorSystem.create();
    }

    @AfterClass
    public static void shutdown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void mergeMetadataStreams() {
        final Duration toleranceWindow = Duration.ofHours(1L);

        final Source<Metadata, NotUsed> persisted = Source.from(List.of(
                Metadata.of(ThingId.of("x:0-only-persisted"), 1L, PolicyTag.of(PolicyId.of("x:0"), 0L), null, Set.of(), null),
                Metadata.of(ThingId.of("x:2-within-tolerance"), 3L, null, null, Set.of(), null),
                Metadata.of(ThingId.of("x:3-revision-mismatch"), 3L, PolicyTag.of(PolicyId.of("x:3"), 0L), null, Set.of(),
                        null),
                Metadata.of(ThingId.of("x:4-policy-id-mismatch"), 3L, PolicyTag.of(PolicyId.of("x:4"), 0L), null, Set.of(),
                        null),
                Metadata.of(ThingId.of("x:5-policy-revision-mismatch"), 3L, PolicyTag.of(PolicyId.of("x:5"), 0L), null,
                        Set.of(), null),
                Metadata.of(ThingId.of("x:6-all-up-to-date"), 3L, PolicyTag.of(PolicyId.of("x:6"), 0L), null, Set.of(), null),
                Metadata.of(ThingId.of("x:7-policy-deleted"), 7L, PolicyTag.of(PolicyId.of("x:7"), 0L), null, Set.of(), null)
        ));

        final Source<Metadata, NotUsed> indexed = Source.from(List.of(
                Metadata.of(ThingId.of("x:1-only-indexed"), 1L, null, null, Set.of(), null),
                Metadata.of(ThingId.of("x:2-within-tolerance"), 1L, null, null, Set.of(), Instant.now(), null),
                Metadata.of(ThingId.of("x:3-revision-mismatch"), 2L, PolicyTag.of(PolicyId.of("x:3"), 1L), null, Set.of(),
                        null),
                Metadata.of(ThingId.of("x:4-policy-id-mismatch"), 3L, PolicyTag.of(PolicyId.of("x:mismatched"), 0L),
                        null, Set.of(), null),
                Metadata.of(ThingId.of("x:5-policy-revision-mismatch"), 3L, PolicyTag.of(PolicyId.of("x:5"), 3L),
                        null, Set.of(), null),
                Metadata.of(ThingId.of("x:6-all-up-to-date"), 5L, PolicyTag.of(PolicyId.of("x:6"), 6L), null, Set.of(), null)
        ));

        new TestKit(actorSystem) {{
            final BackgroundSyncStream underTest =
                    BackgroundSyncStream.of(getRef(), Duration.ofSeconds(3L), toleranceWindow, 100,
                            Duration.ofSeconds(10L), DefaultNamespacePoliciesConfig.of(ConfigFactory.empty()));
            final CompletionStage<List<String>> inconsistentThingIds =
                    underTest.filterForInconsistencies(persisted, indexed)
                            .map(metadata -> metadata.getThingId().toString())
                            .runWith(Sink.seq(), actorSystem);

            expectMsg(SudoRetrievePolicyRevision.of(PolicyId.of("x:0"), DittoHeaders.empty()));
            reply(SudoRetrievePolicyRevisionResponse.of(PolicyId.of("x:0"), 0L, DittoHeaders.empty()));

            expectAndReplySudoRetrievePolicy(this, PolicyId.of("x:5"), 6L);

            expectAndReplySudoRetrievePolicy(this, PolicyId.of("x:6"), 6L);

            expectMsg(SudoRetrievePolicyRevision.of(PolicyId.of("x:7"), DittoHeaders.empty()));
            reply(PolicyNotAccessibleException.newBuilder(PolicyId.of("x:7")).build());

            assertThat(inconsistentThingIds.toCompletableFuture().join()).containsExactly(
                    "x:0-only-persisted",
                    "x:1-only-indexed",
                    "x:3-revision-mismatch",
                    "x:4-policy-id-mismatch",
                    "x:5-policy-revision-mismatch"
            );
        }};
    }

    @Test
    public void namespaceRootPoliciesAreConsideredConsistentDuringBackgroundSync() {
        final Duration toleranceWindow = Duration.ofHours(1L);
        final PolicyId thingPolicyId = PolicyId.of("org.example.devices", "thing-policy");
        final PolicyId rootPolicyId = PolicyId.of("org.example", "tenant-root");

        final Source<Metadata, NotUsed> persisted = Source.from(List.of(
                Metadata.of(ThingId.of("x:8-namespace-root"), 3L, PolicyTag.of(thingPolicyId, 7L), null, Set.of(), null)
        ));
        final Source<Metadata, NotUsed> indexed = Source.from(List.of(
                Metadata.of(ThingId.of("x:8-namespace-root"), 3L, PolicyTag.of(thingPolicyId, 7L), null,
                        Set.of(PolicyTag.of(rootPolicyId, 8L)), null)
        ));

        new TestKit(actorSystem) {{
            final BackgroundSyncStream underTest =
                    BackgroundSyncStream.of(getRef(), Duration.ofSeconds(3L), toleranceWindow, 100,
                            Duration.ofSeconds(10L), DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                            "ditto.namespace-policies {\n" +
                            "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                            "}")));
            final CompletionStage<List<String>> inconsistentThingIds =
                    underTest.filterForInconsistencies(persisted, indexed)
                            .map(metadata -> metadata.getThingId().toString())
                            .runWith(Sink.seq(), actorSystem);

            expectMsg(SudoRetrievePolicy.of(thingPolicyId, DittoHeaders.empty()));
            reply(SudoRetrievePolicyResponse.of(thingPolicyId, policyWithoutImports(thingPolicyId, 7L),
                    DittoHeaders.empty()));

            expectMsg(SudoRetrievePolicy.of(rootPolicyId, DittoHeaders.empty()));
            reply(SudoRetrievePolicyResponse.of(rootPolicyId, policyWithoutImports(rootPolicyId, 8L),
                    DittoHeaders.empty()));

            expectMsg(SudoRetrievePolicyRevision.of(rootPolicyId, DittoHeaders.empty()));
            reply(SudoRetrievePolicyRevisionResponse.of(rootPolicyId, 8L, DittoHeaders.empty()));

            assertThat(inconsistentThingIds.toCompletableFuture().join()).isEmpty();
        }};
    }

    @Test
    public void missingNamespaceRootPolicyTagInIndexTriggersReIndexing() {
        final Duration toleranceWindow = Duration.ofHours(1L);
        final PolicyId thingPolicyId = PolicyId.of("org.example.devices", "thing-policy");
        final PolicyId rootPolicyId = PolicyId.of("org.example", "tenant-root");

        // Persisted and indexed have matching thing revision and policy revision,
        // but the indexed entry is missing the namespace root policy tag in allReferencedPolicyTags.
        final Source<Metadata, NotUsed> persisted = Source.from(List.of(
                Metadata.of(ThingId.of("x:9-missing-root-tag"), 3L,
                        PolicyTag.of(thingPolicyId, 7L), null, Set.of(), null)
        ));
        final Source<Metadata, NotUsed> indexed = Source.from(List.of(
                Metadata.of(ThingId.of("x:9-missing-root-tag"), 3L,
                        PolicyTag.of(thingPolicyId, 7L), null, Set.of(), null)
        ));

        new TestKit(actorSystem) {{
            final BackgroundSyncStream underTest =
                    BackgroundSyncStream.of(getRef(), Duration.ofSeconds(3L), toleranceWindow, 100,
                            Duration.ofSeconds(10L), DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                            "ditto.namespace-policies {\n" +
                            "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                            "}")));
            final CompletionStage<List<String>> inconsistentThingIds =
                    underTest.filterForInconsistencies(persisted, indexed)
                            .map(metadata -> metadata.getThingId().toString())
                            .runWith(Sink.seq(), actorSystem);

            // BackgroundSyncStream retrieves the thing's policy to check consistency
            expectMsg(SudoRetrievePolicy.of(thingPolicyId, DittoHeaders.empty()));
            reply(SudoRetrievePolicyResponse.of(thingPolicyId, policyWithoutImports(thingPolicyId, 7L),
                    DittoHeaders.empty()));

            // BackgroundSyncStream retrieves the namespace root policy to compute expected references
            expectMsg(SudoRetrievePolicy.of(rootPolicyId, DittoHeaders.empty()));
            reply(SudoRetrievePolicyResponse.of(rootPolicyId, policyWithoutImports(rootPolicyId, 8L),
                    DittoHeaders.empty()));

            // The indexed entry has no root policy tag, but the expected set includes it —
            // the set-equality check fails and the entry is flagged as inconsistent.
            assertThat(inconsistentThingIds.toCompletableFuture().join())
                    .containsExactly("x:9-missing-root-tag");
        }};
    }

    @Test
    public void missingTransitiveImportPolicyTagInIndexTriggersReIndexing() {
        // Scenario: A → B → C → D (3-level transitive chain)
        // Policy A imports B with transitiveImports: ["C"]
        // Policy B imports C with transitiveImports: ["D"]
        // The indexed entry is missing the transitive policy tags for C and D
        // → should be flagged as inconsistent and trigger re-indexing

        final Duration toleranceWindow = Duration.ofHours(1L);
        final PolicyId policyIdA = PolicyId.of("com.example.thing", "policy-a");
        final PolicyId policyIdB = PolicyId.of("com.example", "intermediate-b");
        final PolicyId policyIdC = PolicyId.of("com.example", "intermediate-c");
        final PolicyId policyIdD = PolicyId.of("com.example", "template-d");

        // Persisted thing uses policy A at revision 5
        final Source<Metadata, NotUsed> persisted = Source.from(List.of(
                Metadata.of(ThingId.of("x:10-transitive-missing"), 3L,
                        PolicyTag.of(policyIdA, 5L), null, Set.of(), null)
        ));
        // Indexed entry has policy A tag and B tag, but is MISSING C and D tags
        final Source<Metadata, NotUsed> indexed = Source.from(List.of(
                Metadata.of(ThingId.of("x:10-transitive-missing"), 3L,
                        PolicyTag.of(policyIdA, 5L), null,
                        Set.of(PolicyTag.of(policyIdB, 2L)), null)
        ));

        new TestKit(actorSystem) {{
            final BackgroundSyncStream underTest =
                    BackgroundSyncStream.of(getRef(), Duration.ofSeconds(3L), toleranceWindow, 100,
                            Duration.ofSeconds(10L),
                            DefaultNamespacePoliciesConfig.of(ConfigFactory.empty()));
            final CompletionStage<List<String>> inconsistentThingIds =
                    underTest.filterForInconsistencies(persisted, indexed)
                            .map(metadata -> metadata.getThingId().toString())
                            .runWith(Sink.seq(), actorSystem);

            // BackgroundSyncStream retrieves policy A to check consistency
            expectMsg(SudoRetrievePolicy.of(policyIdA, DittoHeaders.empty()));

            // Policy A: imports B with transitiveImports: ["C"]
            final Policy policyA = Policy.newBuilder(policyIdA)
                    .setRevision(5L)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB,
                            PoliciesModelFactory.newEffectedImportedLabels(
                                    java.util.Collections.emptyList(),
                                    List.of(policyIdC))))
                    .build();
            reply(SudoRetrievePolicyResponse.of(policyIdA, policyA, DittoHeaders.empty()));

            // The expected referenced policy IDs are: B (direct import), C (transitiveImports from A's import of B)
            // The indexed entry only has B → set mismatch → flagged as inconsistent
            // No further messages expected since the set comparison already fails

            assertThat(inconsistentThingIds.toCompletableFuture().join())
                    .containsExactly("x:10-transitive-missing");
        }};
    }

    @Test
    public void transitiveImportPolicyTagsInIndexAreConsideredConsistent() {
        // Same 3-level scenario, but the indexed entry HAS all the expected transitive policy tags
        // → should be considered consistent (no re-indexing needed)

        final Duration toleranceWindow = Duration.ofHours(1L);
        final PolicyId policyIdA = PolicyId.of("com.example.thing", "policy-a2");
        final PolicyId policyIdB = PolicyId.of("com.example", "intermediate-b2");
        final PolicyId policyIdC = PolicyId.of("com.example", "intermediate-c2");
        final PolicyId policyIdD = PolicyId.of("com.example", "template-d2");

        // Policy A imports B with transitiveImports: ["C"]
        // B imports C with transitiveImports: ["D"]
        // Expected referenced policies: B, C, D (all transitive dependencies)

        final Source<Metadata, NotUsed> persisted = Source.from(List.of(
                Metadata.of(ThingId.of("x:11-transitive-consistent"), 3L,
                        PolicyTag.of(policyIdA, 5L), null, Set.of(), null)
        ));
        // Indexed entry includes ALL expected transitive tags
        final Source<Metadata, NotUsed> indexed = Source.from(List.of(
                Metadata.of(ThingId.of("x:11-transitive-consistent"), 3L,
                        PolicyTag.of(policyIdA, 5L), null,
                        Set.of(PolicyTag.of(policyIdB, 2L),
                                PolicyTag.of(policyIdC, 3L),
                                PolicyTag.of(policyIdD, 4L)), null)
        ));

        new TestKit(actorSystem) {{
            final BackgroundSyncStream underTest =
                    BackgroundSyncStream.of(getRef(), Duration.ofSeconds(3L), toleranceWindow, 100,
                            Duration.ofSeconds(10L),
                            DefaultNamespacePoliciesConfig.of(ConfigFactory.empty()));
            final CompletionStage<List<String>> inconsistentThingIds =
                    underTest.filterForInconsistencies(persisted, indexed)
                            .map(metadata -> metadata.getThingId().toString())
                            .runWith(Sink.seq(), actorSystem);

            // BackgroundSyncStream retrieves policy A
            expectMsg(SudoRetrievePolicy.of(policyIdA, DittoHeaders.empty()));

            // Policy A: imports B with transitiveImports: ["C"]
            // B's import of C has transitiveImports: ["D"]
            final Policy policyA = Policy.newBuilder(policyIdA)
                    .setRevision(5L)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB,
                            PoliciesModelFactory.newEffectedImportedLabels(
                                    java.util.Collections.emptyList(),
                                    List.of(policyIdC, policyIdD))))
                    .build();
            reply(SudoRetrievePolicyResponse.of(policyIdA, policyA, DittoHeaders.empty()));

            // Verify revision of each referenced policy (order is non-deterministic)
            for (int i = 0; i < 3; i++) {
                final SudoRetrievePolicyRevision revisionCmd =
                        expectMsgClass(SudoRetrievePolicyRevision.class);
                final PolicyId requestedId = revisionCmd.getEntityId();
                final long revision;
                if (requestedId.equals(policyIdB)) {
                    revision = 2L;
                } else if (requestedId.equals(policyIdC)) {
                    revision = 3L;
                } else if (requestedId.equals(policyIdD)) {
                    revision = 4L;
                } else {
                    throw new AssertionError("Unexpected policy revision request for: " + requestedId);
                }
                reply(SudoRetrievePolicyRevisionResponse.of(requestedId, revision, DittoHeaders.empty()));
            }

            // All tags match → consistent → empty result
            assertThat(inconsistentThingIds.toCompletableFuture().join()).isEmpty();
        }};
    }

    @Test
    public void removedTransitiveImportMakesStaleIndexEntryInconsistent() {
        // Scenario: Policy A previously had transitiveImports: ["C"] on its import of B.
        // The indexed entry has B and C in __referencedPolicies.
        // Policy A is updated to remove transitiveImports (now only imports B without transitiveImports).
        // The expected set is now just {B}, but the indexed entry still has {B, C}
        // → the set-equality check fails and the entry is flagged as inconsistent for re-indexing.

        final Duration toleranceWindow = Duration.ofHours(1L);
        final PolicyId policyIdA = PolicyId.of("com.example.thing", "policy-a3");
        final PolicyId policyIdB = PolicyId.of("com.example", "intermediate-b3");
        final PolicyId policyIdC = PolicyId.of("com.example", "template-c3");

        // Persisted thing uses policy A at revision 6 (updated, transitiveImports removed)
        final Source<Metadata, NotUsed> persisted = Source.from(List.of(
                Metadata.of(ThingId.of("x:12-transitive-removed"), 3L,
                        PolicyTag.of(policyIdA, 6L), null, Set.of(), null)
        ));
        // Indexed entry still has the OLD __referencedPolicies with both B and C
        final Source<Metadata, NotUsed> indexed = Source.from(List.of(
                Metadata.of(ThingId.of("x:12-transitive-removed"), 3L,
                        PolicyTag.of(policyIdA, 5L), null,
                        Set.of(PolicyTag.of(policyIdB, 2L),
                                PolicyTag.of(policyIdC, 3L)), null)
        ));

        new TestKit(actorSystem) {{
            final BackgroundSyncStream underTest =
                    BackgroundSyncStream.of(getRef(), Duration.ofSeconds(3L), toleranceWindow, 100,
                            Duration.ofSeconds(10L),
                            DefaultNamespacePoliciesConfig.of(ConfigFactory.empty()));
            final CompletionStage<List<String>> inconsistentThingIds =
                    underTest.filterForInconsistencies(persisted, indexed)
                            .map(metadata -> metadata.getThingId().toString())
                            .runWith(Sink.seq(), actorSystem);

            // BackgroundSyncStream retrieves policy A (updated version without transitiveImports)
            expectMsg(SudoRetrievePolicy.of(policyIdA, DittoHeaders.empty()));

            // Policy A now only imports B (no transitiveImports)
            final Policy updatedPolicyA = Policy.newBuilder(policyIdA)
                    .setRevision(6L)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB))
                    .build();
            reply(SudoRetrievePolicyResponse.of(policyIdA, updatedPolicyA, DittoHeaders.empty()));

            // Policy revision mismatch (indexed has rev 5, persisted has rev 6) already triggers
            // inconsistency, but even if revisions matched, the set {B} ≠ {B, C} would catch it.
            assertThat(inconsistentThingIds.toCompletableFuture().join())
                    .containsExactly("x:12-transitive-removed");
        }};
    }

    @Test
    public void importDeclaredPurelyForEntryReferenceIsTrackedInExpectedReferencedPolicies() {
        // Scenario: Policy A imports policy B with no `entries` filter and no `transitiveImports`.
        // The reason A imports B is so that one of A's entries can use a `references` field
        // pointing into B (write-time validation requires every import-reference target to be a
        // declared import — see PolicyImportsPreEnforcer.validateReferencesModification).
        // The dependency from A to B is therefore captured by A's `imports` block, regardless of
        // whether A pulls any entries from B implicitly. BackgroundSyncStream's expected
        // referenced-policies set must include B, and an indexed entry that does not have B's
        // PolicyTag must be flagged as inconsistent so the thing gets re-indexed when B changes.

        final Duration toleranceWindow = Duration.ofHours(1L);
        final PolicyId policyIdA = PolicyId.of("com.example.thing", "policy-a-refs");
        final PolicyId policyIdB = PolicyId.of("com.example", "template-b-refs");

        final Source<Metadata, NotUsed> persisted = Source.from(List.of(
                Metadata.of(ThingId.of("x:13-references-stale"), 3L,
                        PolicyTag.of(policyIdA, 5L), null, Set.of(), null)
        ));
        // Indexed metadata has policy A's tag at the right revision but does NOT have B's tag —
        // simulating an out-of-date index that hasn't been told B is a dependency.
        final Source<Metadata, NotUsed> indexed = Source.from(List.of(
                Metadata.of(ThingId.of("x:13-references-stale"), 3L,
                        PolicyTag.of(policyIdA, 5L), null, Set.of(), null)
        ));

        new TestKit(actorSystem) {{
            final BackgroundSyncStream underTest =
                    BackgroundSyncStream.of(getRef(), Duration.ofSeconds(3L), toleranceWindow, 100,
                            Duration.ofSeconds(10L),
                            DefaultNamespacePoliciesConfig.of(ConfigFactory.empty()));
            final CompletionStage<List<String>> inconsistentThingIds =
                    underTest.filterForInconsistencies(persisted, indexed)
                            .map(metadata -> metadata.getThingId().toString())
                            .runWith(Sink.seq(), actorSystem);

            expectMsg(SudoRetrievePolicy.of(policyIdA, DittoHeaders.empty()));

            // Policy A: imports B (default empty effected imports, no transitiveImports). One entry
            // "admin" inherits resources from B's "role" via a `references` field.
            final Policy policyA = Policy.newBuilder(policyIdA)
                    .setRevision(5L)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB))
                    .forLabel("admin")
                    .setSubject(SubjectIssuer.GOOGLE, "alice", SubjectType.GENERATED)
                    .setReferencesFor("admin", List.of(
                            PoliciesModelFactory.newEntryReference(policyIdB, Label.of("role"))))
                    .build();
            reply(SudoRetrievePolicyResponse.of(policyIdA, policyA, DittoHeaders.empty()));

            // Expected referenced policies: {B}. Indexed has empty set → mismatch → inconsistent.
            // No SudoRetrievePolicyRevision is expected because the set-equality check fails first.
            assertThat(inconsistentThingIds.toCompletableFuture().join())
                    .containsExactly("x:13-references-stale");
        }};
    }

    private void expectAndReplySudoRetrievePolicy(final TestKit testKit, final PolicyId policyId, final Long revision) {
        final DittoHeaders empty = DittoHeaders.empty();
        testKit.expectMsg(SudoRetrievePolicy.of(policyId, empty));
        testKit.reply(SudoRetrievePolicyResponse.of(policyId, policyWithoutImports(policyId, revision), empty));
    }

    private Policy policyWithoutImports(final PolicyId policyId, final Long revision) {
        return Policy.newBuilder(policyId)
                .setRevision(revision)
                .build();
    }

}
