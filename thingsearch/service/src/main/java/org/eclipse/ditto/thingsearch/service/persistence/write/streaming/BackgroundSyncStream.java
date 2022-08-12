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

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.internal.utils.akka.controlflow.MergeSortedAsPair;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyRevision;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyRevisionResponse;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.javadsl.Source;

/**
 * Merging a stream of thing snapshots with a stream of metadata from the search index to detect
 * out-of-date search index entries.
 */
public final class BackgroundSyncStream {

    private static final ThingId EMPTY_THING_ID = ThingId.of(LowerBound.emptyEntityId(ThingConstants.ENTITY_TYPE));
    private static final PolicyId EMPTY_POLICY_ID = PolicyId.of(LowerBound.emptyEntityId(PolicyConstants.ENTITY_TYPE));

    private final ActorRef policiesShardRegion;
    private final Duration policiesAskTimeout;
    private final Duration toleranceWindow;
    private final int throttleThroughput;
    private final Duration throttlePeriod;

    private BackgroundSyncStream(
            final ActorRef policiesShardRegion,
            final Duration policiesAskTimeout,
            final Duration toleranceWindow,
            final int throttleThroughput,
            final Duration throttlePeriod) {
        this.policiesShardRegion = policiesShardRegion;
        this.policiesAskTimeout = policiesAskTimeout;
        this.toleranceWindow = toleranceWindow;
        this.throttleThroughput = throttleThroughput;
        this.throttlePeriod = throttlePeriod;
    }

    /**
     * Create a background sync stream.
     *
     * @param policiesShardRegion the policies shard region.
     * @param policiesAskTimeout ask timeout for messages to the policies shard region.
     * @param toleranceWindow time window of recent updates not considered for background sync.
     * @param throttleThroughput how many messages to let through per throttle period.
     * @param throttlePeriod the throttle period.
     * @return the background sync stream.
     */
    public static BackgroundSyncStream of(
            final ActorRef policiesShardRegion,
            final Duration policiesAskTimeout,
            final Duration toleranceWindow,
            final int throttleThroughput,
            final Duration throttlePeriod) {

        return new BackgroundSyncStream(policiesShardRegion, policiesAskTimeout, toleranceWindow, throttleThroughput,
                throttlePeriod);
    }

    /**
     * Discover inconsistencies between the persisted and indexed metadata and emit extra/nonexistent/mismatched
     * entries of the search index.
     *
     * @param metadataFromSnapshots metadata streamed from the things snapshot store.
     * @param metadataFromSearchIndex metadata streamed from the search index.
     * @return source of inconsistent entries.
     */
    public Source<Metadata, NotUsed> filterForInconsistencies(final Source<Metadata, ?> metadataFromSnapshots,
            final Source<Metadata, ?> metadataFromSearchIndex) {
        final Comparator<Metadata> comparator = BackgroundSyncStream::compareMetadata;

        return MergeSortedAsPair.merge(emptyMetadata(), comparator, metadataFromSnapshots, metadataFromSearchIndex)
                .throttle(throttleThroughput, throttlePeriod)
                .flatMapConcat(this::filterForInconsistency);
    }

    private static boolean isInsideToleranceWindow(final Metadata metadata, final Instant toleranceCutOff) {
        return metadata.getModified()
                .map(modified -> modified.isAfter(toleranceCutOff))
                .orElse(false);
    }

    private static Metadata emptyMetadata() {
        return Metadata.of(EMPTY_THING_ID, 0L, EMPTY_POLICY_ID, 0L, null);
    }

    private Source<Metadata, NotUsed> filterForInconsistency(final Pair<Metadata, Metadata> pair) {
        final Metadata persisted = pair.first();
        final Metadata indexed = pair.second();
        final int comparison = compareMetadata(persisted, indexed);
        final Instant toleranceCutOff = Instant.now().minus(toleranceWindow);
        if (comparison < 0) {
            // persisted thing is not in search index; trigger update if the snapshot is not too recent
            return isInsideToleranceWindow(persisted, toleranceCutOff)
                    ? Source.empty()
                    : confirmPersistedAndNotIndexed(persisted);
        } else if (comparison > 0) {
            // indexed thing is not persisted; trigger update if the index entry is not too recent
            return isInsideToleranceWindow(indexed, toleranceCutOff)
                    ? Source.empty()
                    : Source.single(indexed).log("NotPersistedAndIndexed");
        } else {
            // IDs match
            if (indexed.getThingId().equals(EMPTY_THING_ID)) {
                // sanity check: entry should not be dummy
                return Source.failed(new IllegalStateException("Unexpected double-dummy entry: " + pair));
            } else if (isInsideToleranceWindow(indexed, toleranceCutOff)) {
                // ignore entries within tolerance window
                return Source.empty();
            } else {
                return emitUnlessConsistent(persisted, indexed);
            }
        }
    }

    private Source<Metadata, NotUsed> confirmPersistedAndNotIndexed(final Metadata persisted) {
        return Source.single(persisted)
                .flatMapConcat(this::retainUnlessPolicyNonexistent)
                .log("PersistedAndNotIndexed");
    }

    /**
     * Emit metadata to trigger index update if the persistence snapshot and the search index entry are inconsistent.
     * Precondition: the thing IDs are identical and the search index entry is outside the tolerance window.
     *
     * @param persisted metadata from the snapshot store of the persistence.
     * @param indexed metadata from the search index with the same thing ID.
     * @return source of a metadata if the persistence and search index are inconsistent, or an empty source otherwise.
     */
    private Source<Metadata, NotUsed> emitUnlessConsistent(final Metadata persisted, final Metadata indexed) {
        if (persisted.getThingRevision() > indexed.getThingRevision()) {
            return Source.single(indexed.invalidateCaches(true, false)).log("RevisionMismatch");
        } else {
            final Optional<PolicyId> persistedPolicyId = persisted.getPolicyId();
            final Optional<PolicyId> indexedPolicyId = indexed.getPolicyId();
            // policy IDs are equal and nonempty; retrieve and compare policy revision
            // policy IDs are empty - the entries are consistent.
            if (!persistedPolicyId.equals(indexedPolicyId)) {
                return Source.single(indexed.invalidateCaches(false, true)).log("PolicyIdMismatch");
            } else {
                return persistedPolicyId.map(policyId -> retrievePolicyRevisionAndEmitMismatch(policyId, indexed))
                        .orElseGet(Source::empty);
            }
        }
    }

    /**
     * Check a PersistedAndNotIndexed entry whether it should trigger an index update.
     * Such an entry should trigger an update unless it has a nonexistent policy.
     *
     * @param persisted the persisted and not indexed entry.
     * @return source of index updates.
     */
    private Source<Metadata, ?> retainUnlessPolicyNonexistent(final Metadata persisted) {
        final Optional<PolicyId> optionalPolicyId = persisted.getPolicyId();
        if (optionalPolicyId.isPresent()) {
            // policy ID exists: entry should be updated if and only if the policy exists
            final SudoRetrievePolicyRevision command =
                    SudoRetrievePolicyRevision.of(optionalPolicyId.get(), DittoHeaders.empty());
            final CompletionStage<Source<Metadata, NotUsed>> askFuture =
                    Patterns.ask(policiesShardRegion, command, policiesAskTimeout)
                            .handle((response, error) -> response instanceof SudoRetrievePolicyRevisionResponse
                                    ? Source.single(persisted)
                                    : Source.empty()
                            );
            return Source.completionStageSource(askFuture);
        } else {
            // policy ID does not exist: entry should not be updated in search index
            return Source.empty();
        }
    }

    private Source<Metadata, NotUsed> retrievePolicyRevisionAndEmitMismatch(final PolicyId policyId,
            final Metadata indexed) {
        final SudoRetrievePolicyRevision command =
                SudoRetrievePolicyRevision.of(policyId, DittoHeaders.empty());
        final CompletionStage<Source<Metadata, NotUsed>> sourceCompletionStage =
                Patterns.ask(policiesShardRegion, command, policiesAskTimeout)
                        .handle((response, error) -> {
                            if (error != null) {
                                return Source.single(error)
                                        .log("ErrorRetrievingPolicyRevision " + policyId)
                                        .map(e -> indexed.invalidateCaches(true, true));
                            } else if (response instanceof SudoRetrievePolicyRevisionResponse sudoRetrievePolicyRevisionResponse) {
                                final long revision = sudoRetrievePolicyRevisionResponse.getRevision();
                                return indexed.getPolicyRevision()
                                        .filter(indexedPolicyRevision -> indexedPolicyRevision.equals(revision))
                                        .map(indexedPolicyRevision -> Source.<Metadata>empty())
                                        .orElseGet(() -> Source.single(indexed.invalidateCaches(false, true))
                                                .log("PolicyRevisionMismatch"));
                            } else {
                                return Source.single(response)
                                        .log("UnexpectedPolicyResponse")
                                        .map(r -> indexed.invalidateCaches(true, true));
                            }
                        });

        return Source.completionStageSource(sourceCompletionStage)
                .mapMaterializedValue(ignored -> NotUsed.getInstance());
    }

    private static int compareMetadata(final Metadata metadata1, final Metadata metadata2) {
        return compareThingIds(metadata1.getThingId(), metadata2.getThingId());
    }

    /**
     * Compare 2 thing IDs according to the processing order of this stream.
     *
     * @param thingId1 the first thing ID.
     * @param thingId2 the second thing ID.
     * @return a positive integer if the first thing ID is bigger, a negative integer if the second
     * thing ID is bigger, and 0 if both are equal.
     */
    public static int compareThingIds(final ThingId thingId1, final ThingId thingId2) {
        final int emptyThingComparison =
                Boolean.compare(thingId1.equals(EMPTY_THING_ID), thingId2.equals(EMPTY_THING_ID));

        return emptyThingComparison != 0 ? emptyThingComparison : thingId1.compareTo(thingId2);
    }

}
