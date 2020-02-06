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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyRevision;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyRevisionResponse;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.utils.akka.controlflow.MergeSortedAsPair;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.Attributes;
import akka.stream.javadsl.Source;

/**
 * Merging a stream of thing snapshots with a stream of metadata from the search index to detect
 * out-of-date search index entries.
 */
public final class BackgroundSyncStream {

    private final ActorRef policiesShardRegion;
    private final Duration policiesAskTimeout;
    private final Duration toleranceWindow;
    private final int throttleThroughput;
    private final Duration throttlePeriod;

    private BackgroundSyncStream(final ActorRef policiesShardRegion,
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

    public static BackgroundSyncStream of(final ActorRef policiesShardRegion,
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
        final Source<Pair<Metadata, Metadata>, NotUsed> merged =
                MergeSortedAsPair.merge(dummyMetadata(), comparator, metadataFromSnapshots, metadataFromSearchIndex)
                        .throttle(throttleThroughput, throttlePeriod);

        // TODO: bookmark thing ID before filter!

        return merged.flatMapConcat(this::filterForInconsistency)
                // log elements at warning level because out-of-date metadata are detected
                .withAttributes(Attributes.logLevels(
                        Attributes.logLevelWarning(),
                        Attributes.logLevelDebug(),
                        Attributes.logLevelError()));
    }

    private boolean isInsideToleranceWindow(final Metadata metadata, final Instant toleranceCutOff) {
        return metadata.getModified()
                .map(modified -> modified.isAfter(toleranceCutOff))
                .orElse(false);
    }

    private static Metadata dummyMetadata() {
        return Metadata.of(ThingId.dummy(), 0L, "", 0L);
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
                    : Source.single(persisted).log("PersistedAndNotIndexed");
        } else if (comparison > 0) {
            // indexed thing is not persisted; trigger update if the index entry is not too recent
            return isInsideToleranceWindow(indexed, toleranceCutOff)
                    ? Source.empty()
                    : Source.single(indexed).log("NotPersistedAndIndexed");
        } else {
            // IDs match
            final String loggerTag = "PersistedAndIndexed";
            if (indexed.getThingId().isDummy()) {
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
            return Source.single(indexed).log("RevisionMismatch");
        } else {
            final String persistedPolicyId = persisted.getPolicyIdInPersistence();
            final String indexedPolicyId = indexed.getPolicyIdInPersistence();
            if (!persistedPolicyId.equals(indexedPolicyId)) {
                return Source.single(indexed).log("PolicyIdMismatch");
            } else if (!persistedPolicyId.isEmpty()) {
                // policy IDs are equal and nonempty; retrieve and compare policy revision
                return retrievePolicyRevisionAndEmitMismatch(persistedPolicyId, indexed);
            } else {
                // policy IDs are empty - the entries are consistent.
                return Source.empty();
            }
        }
    }

    private Source<Metadata, NotUsed> retrievePolicyRevisionAndEmitMismatch(final String policyId,
            final Metadata indexed) {
        final SudoRetrievePolicyRevision command =
                SudoRetrievePolicyRevision.of(PolicyId.of(policyId), DittoHeaders.empty());
        final CompletionStage<Source<Metadata, NotUsed>> sourceCompletionStage =
                Patterns.ask(policiesShardRegion, command, policiesAskTimeout)
                        .handle((response, error) -> {
                            if (error != null) {
                                return Source.single(error)
                                        .log("ErrorRetrievingPolicyRevision " + policyId)
                                        .map(e -> indexed);
                            } else if (response instanceof SudoRetrievePolicyRevisionResponse) {
                                final long revision = ((SudoRetrievePolicyRevisionResponse) response).getRevision();
                                return revision == indexed.getPolicyRevision()
                                        ? Source.empty()
                                        : Source.single(indexed).log("PolicyRevisionMismatch");
                            } else {
                                return Source.single(response)
                                        .log("UnexpectedPolicyResponse")
                                        .map(r -> indexed);
                            }
                        });
        return Source.fromSourceCompletionStage(sourceCompletionStage)
                .mapMaterializedValue(ignored -> NotUsed.getInstance());
    }

    private static int compareMetadata(final Metadata metadata1, final Metadata metadata2) {
        return compareThingIds(metadata1.getThingId(), metadata2.getThingId());
    }

    private static int compareThingIds(final ThingId thingId1, final ThingId thingId2) {
        final int dummyComparison = Boolean.compare(thingId1.isDummy(), thingId2.isDummy());
        return dummyComparison != 0 ? dummyComparison : thingId1.compareTo(thingId2);
    }
}
