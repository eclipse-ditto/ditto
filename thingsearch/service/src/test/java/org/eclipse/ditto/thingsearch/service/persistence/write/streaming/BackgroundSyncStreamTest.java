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
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
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
