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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.services.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;

/**
 * Source of metadata streamed from things-service.
 */
final class ThingsMetadataSource {

    private static final String REVISION = "_revision";
    private static final String POLICY_ID = "policyId";
    private static final String MODIFIED = "_modified";

    private static final List<String> SNAPSHOT_FIELDS = List.of(REVISION, POLICY_ID, MODIFIED);

    private final ActorRef pubSubMediator;
    private final int burst;
    private final Duration idleTimeout;

    private ThingsMetadataSource(final ActorRef pubSubMediator, final int burst, final Duration idleTimeout) {
        this.pubSubMediator = pubSubMediator;
        this.burst = burst;
        this.idleTimeout = idleTimeout;
    }

    static ThingsMetadataSource of(final ActorRef pubSubMediator, final int burst, final Duration idleTimeout) {
        return new ThingsMetadataSource(pubSubMediator, burst, idleTimeout);
    }

    /**
     * Start a stream of metadata from Things persistence.
     *
     * @param lowerBound the lower bound thing ID - may come from the bookmark.
     * @return source of metadata streamed from Things via a resume-source.
     */
    Source<Metadata, NotUsed> createSource(final ThingId lowerBound) {
        return requestStream(lowerBound)
                .flatMapConcat(ThingsMetadataSource::getStreamedSnapshots)
                .map(ThingsMetadataSource::toMetadata);
    }

    private Object getStartStreamCommand(final ThingId lowerBound) {
        final SudoStreamSnapshots commandWithoutLowerBound =
                SudoStreamSnapshots.of(burst, idleTimeout.toMillis(), SNAPSHOT_FIELDS, DittoHeaders.empty());
        final SudoStreamSnapshots command =
                lowerBound.isDummy() ? commandWithoutLowerBound : commandWithoutLowerBound.withLowerBound(lowerBound);
        return DistPubSubAccess.send(ThingsMessagingConstants.THINGS_SNAPSHOT_STREAMING_ACTOR_PATH, command);
    }

    private Source<SourceRef<?>, NotUsed> requestStream(final ThingId lowerBound) {
        final Object startStreamCommand = getStartStreamCommand(lowerBound);
        return Source.fromCompletionStage(Patterns.ask(pubSubMediator, startStreamCommand, idleTimeout))
                .flatMapConcat(response -> {
                    if (response instanceof SourceRef<?>) {
                        return Source.single((SourceRef<?>) response);
                    } else {
                        return Source.failed(new ClassCastException("Not a SourceRef: " + response));
                    }
                });
    }

    private static Source<StreamedSnapshot, NotUsed> getStreamedSnapshots(final SourceRef<?> sourceRef) {
        return sourceRef.getSource()
                .map(element -> {
                    if (element instanceof StreamedSnapshot) {
                        return (StreamedSnapshot) element;
                    } else {
                        throw new ClassCastException("Not a StreamedSnapshot: " + element);
                    }
                });
    }

    private static Metadata toMetadata(final StreamedSnapshot streamedSnapshot) {
        final JsonObject snapshot = streamedSnapshot.getSnapshot();
        final ThingId thingId = ThingId.of(streamedSnapshot.getEntityId());
        final long thingRevision = snapshot.getValueOrThrow(Thing.JsonFields.REVISION);
        final PolicyId policyId = snapshot.getValue(Thing.JsonFields.POLICY_ID).map(PolicyId::of).orElse(null);
        final Instant modified = snapshot.getValue(Thing.JsonFields.MODIFIED).map(Instant::parse).orElse(null);
        // policy revision is not known from thing snapshot
        return Metadata.of(thingId, thingRevision, policyId, 0L, modified);
    }
}
