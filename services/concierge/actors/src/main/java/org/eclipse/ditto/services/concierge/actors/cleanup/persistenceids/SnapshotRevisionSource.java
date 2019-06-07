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
import akka.stream.javadsl.Source;

/**
 * Stream all snapshot revisions of all known entities.
 */
public final class SnapshotRevisionSource {

    // TODO: configure?
    private static final List<String> PERSISTENCE_STREAMING_ACTOR_PATHS =
            Arrays.asList(ThingsMessagingConstants.THINGS_STREAM_PROVIDER_ACTOR_PATH,
                    PoliciesMessagingConstants.POLICIES_STREAM_PROVIDER_ACTOR_PATH,
                    ConnectivityMessagingConstants.STREAM_PROVIDER_ACTOR_PATH);

    private static final int BURST = 25;

    // timeout requesting a stream
    private static final Duration INITIAL_TIMEOUT = Duration.ofSeconds(10L);

    // long timeout; do not want to cancel stream if no credit available
    private static final long TIMEOUT_MILLIS = 600_000L;

    /**
     * Create a stream of snapshot revisions of all known entities.
     * The stream fails if there is a failure requesting any stream or processing any stream element.
     *
     * @param pubSubMediator the pub-sub mediator.
     * @return source of entity IDs with revisions of their latest snapshots.
     */
    public static Source<EntityIdWithRevision, NotUsed> create(final ActorRef pubSubMediator) {
        return Source.fromIterator(PERSISTENCE_STREAMING_ACTOR_PATHS::iterator)
                .buffer(1, OverflowStrategy.backpressure())
                .map(SnapshotRevisionSource::requestStreamCommand)
                .mapAsync(1, command -> Patterns.ask(pubSubMediator, command, INITIAL_TIMEOUT))
                .flatMapConcat(SnapshotRevisionSource::handleSourceRef);
    }

    private static DistributedPubSubMediator.Send requestStreamCommand(final String path) {
        return new DistributedPubSubMediator.Send(path, sudoStreamSnapshotRevisions(), false);
    }

    private static SudoStreamSnapshotRevisions sudoStreamSnapshotRevisions() {
        return SudoStreamSnapshotRevisions.of(BURST, TIMEOUT_MILLIS, DittoHeaders.empty());
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
                                Source.fromIterator(batch.getElements()::iterator);
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
