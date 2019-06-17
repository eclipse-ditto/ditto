/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.actors.cleanup.persistenceids;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.concierge.common.PersistenceIdsConfig;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.streaming.SudoStreamSnapshotRevisions;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.pattern.Patterns;
import akka.stream.OverflowStrategy;
import akka.stream.SourceRef;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Source;

/**
 * Stream all snapshot revisions of all known entities.
 */
public final class PersistenceIdSource {

    // TODO: configure?
    private static final List<String> PERSISTENCE_STREAMING_ACTOR_PATHS =
            Arrays.asList(ThingsMessagingConstants.THINGS_STREAM_PROVIDER_ACTOR_PATH,
                    PoliciesMessagingConstants.POLICIES_STREAM_PROVIDER_ACTOR_PATH,
                    ConnectivityMessagingConstants.STREAM_PROVIDER_ACTOR_PATH);

    /**
     * Create a stream of snapshot revisions of all known entities.
     * The stream fails if there is a failure requesting any stream or processing any stream element.
     *
     * @param config configuration of the persistence ID source.
     * @param pubSubMediator the pub-sub mediator.
     * @return source of entity IDs with revisions of their latest snapshots.
     */
    public static Source<EntityIdWithRevision, NotUsed> create(final PersistenceIdsConfig config,
            final ActorRef pubSubMediator) {
        return Source.from(PERSISTENCE_STREAMING_ACTOR_PATHS)
                .buffer(1, OverflowStrategy.backpressure())
                .map(path -> requestStreamCommand(config, path))
                .mapAsync(1, command -> Patterns.ask(pubSubMediator, command, config.getStreamRequestTimeout()))
                .flatMapConcat(PersistenceIdSource::handleSourceRef);
    }

    public static Source<EntityIdWithRevision, NotUsed> createInfiniteSource(final PersistenceIdsConfig config,
            final ActorRef pubSubMediator) {
        final Duration minBackOff = config.getBackOffMin();
        final Duration maxBackOff = config.getBackOffMax();
        final double randomFactor = config.getBackOffRandomFactor();

        // RestartSource generates error logs if the stream restarts due to failure.
        return RestartSource.withBackoff(minBackOff, maxBackOff, randomFactor, () -> create(config, pubSubMediator));
    }

    private static DistributedPubSubMediator.Send requestStreamCommand(final PersistenceIdsConfig config,
            final String path) {
        return new DistributedPubSubMediator.Send(path, sudoStreamSnapshotRevisions(config), false);
    }

    private static SudoStreamSnapshotRevisions sudoStreamSnapshotRevisions(final PersistenceIdsConfig config) {
        return SudoStreamSnapshotRevisions.of(config.getBurst(), config.getStreamIdleTimeout().toMillis(),
                DittoHeaders.empty());
    }

    private static Source<EntityIdWithRevision, NotUsed> handleSourceRef(final Object reply) {
        if (reply instanceof SourceRef) {
            return createSourceFromSourceRef((SourceRef) reply);
        } else {
            return failedSourceDueToUnexpectedMessage("SourceRef", reply);
        }
    }

    private static Source<EntityIdWithRevision, NotUsed> createSourceFromSourceRef(final SourceRef<?> sourceRef) {
        return sourceRef.getSource()
                .flatMapConcat(element -> {
                    if (element instanceof BatchedEntityIdWithRevisions) {
                        final BatchedEntityIdWithRevisions<?> batch = (BatchedEntityIdWithRevisions) element;
                        final Source<? extends EntityIdWithRevision, NotUsed> source =
                                Source.from(batch.getElements());
                        return source.map(x -> x);
                    } else {
                        return failedSourceDueToUnexpectedMessage("BatchedEntityIdWithRevisions", element);
                    }
                });
    }

    private static <T> Source<T, NotUsed> failedSourceDueToUnexpectedMessage(final String expectedMessage,
            final Object actualMessage) {
        final Throwable error;
        if (actualMessage instanceof Throwable) {
            error = (Throwable) actualMessage;
        } else {
            final String message = String.format("While expecting <%s>, got unexpected <%s>", expectedMessage,
                    Objects.toString(actualMessage));
            error = new IllegalStateException(message);
        }
        return Source.failed(error);

    }
}
