/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants.CONNECTION_ID_RETRIEVAL_ACTOR_NAME;
import static org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal.LIFECYCLE;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.pubsub.DistributedPubSub;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionIdsByTag;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionIdsByTagResponse;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityInternalErrorException;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityErrorResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIds;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIdsResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.connectivity.service.config.ConnectionIdsRetrievalConfig;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;

/**
 * Actor handling messages related to connections e.g. retrieving all connections ids.
 */
public final class ConnectionIdsRetrievalActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = CONNECTION_ID_RETRIEVAL_ACTOR_NAME;

    private static final String PERSISTENCE_ID_FIELD = "_id";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Supplier<Source<Document, NotUsed>> persistenceIdsFromJournalSourceSupplier;
    private final Supplier<Source<Document, NotUsed>> persistenceIdsFromSnapshotSourceSupplier;
    private final Materializer materializer;

    private final Function<String, Source<String, NotUsed>> taggedPidSourceFunction;

    @SuppressWarnings("unused")
    private ConnectionIdsRetrievalActor(final MongoReadJournal readJournal,
            final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig) {
        materializer = Materializer.createMaterializer(this::getContext);
        persistenceIdsFromJournalSourceSupplier =
                () -> readJournal.getLatestJournalEntries(connectionIdsRetrievalConfig.getReadJournalBatchSize(),
                        Duration.ofSeconds(1), materializer);
        persistenceIdsFromSnapshotSourceSupplier =
                () -> readJournal.getNewestSnapshotsAbove("", connectionIdsRetrievalConfig.getReadSnapshotBatchSize(),
                        materializer);

        taggedPidSourceFunction =
                tag -> readJournal.getJournalPidsWithTag(tag, connectionIdsRetrievalConfig.getReadJournalBatchSize(),
                        Duration.ofSeconds(1), materializer, true);
    }

    @Override
    public void preStart() {
        final var actorSystem = getContext().getSystem();
        final var pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        final var self = getSelf();
        pubSubMediator.tell(DistPubSubAccess.put(self), self);
    }

    /**
     * Creates Pekko configuration object Props for this Actor.
     *
     * @param readJournal readJournal to extract current PIDs from.
     * @param connectionIdsRetrievalConfig the config to build the pid suppliers from.
     * @return the Pekko configuration Props object.
     */
    public static Props props(final MongoReadJournal readJournal,
            final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig) {
        return Props.create(ConnectionIdsRetrievalActor.class, readJournal, connectionIdsRetrievalConfig);
    }

    private static boolean isDeleted(final Document document) {
        return Optional.ofNullable(document.getString(MongoReadJournal.J_EVENT_MANIFEST))
                .map(ConnectionDeleted.TYPE::equals)
                .orElse(true);
    }

    private static boolean isNotDeleted(final Document document) {
        return Optional.ofNullable(document.getString(MongoReadJournal.J_EVENT_MANIFEST))
                .map(manifest -> !ConnectionDeleted.TYPE.equals(manifest))
                .orElse(false);
    }

    private static boolean snapshotIsNotDeleted(final Document document) {
        return Optional.ofNullable(document.getString(LIFECYCLE))
                .map(lifecycle -> !"DELETED".equals(lifecycle))
                .orElse(false);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveAllConnectionIds.class, this::getAllConnectionIDs)
                .match(SudoRetrieveConnectionIdsByTag.class, this::getConnectionIDsByTag)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void getConnectionIDsByTag(final SudoRetrieveConnectionIdsByTag sudoRetrieveConnectionIdsByTag) {
        final DittoHeaders dittoHeaders = sudoRetrieveConnectionIdsByTag.getDittoHeaders();
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(dittoHeaders);
        final String tag = sudoRetrieveConnectionIdsByTag.getTag();
        l.info("Retrieving connection IDs by tag <{}>: {}", tag, sudoRetrieveConnectionIdsByTag);
        try {
            final ActorRef sender = sender();
            final CompletionStage<SudoRetrieveConnectionIdsByTagResponse>
                    retrieveConnectionIdsByTagResponseCompletionStage =
                    taggedPidSourceFunction.apply(tag)
                            .filter(pid -> pid.startsWith(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX))
                            .map(pid -> pid.substring(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX.length()))
                            .map(ConnectionId::of)
                            .runWith(Sink.seq(), materializer)
                            .thenApply(Set::copyOf)
                            .thenApply(connectionIds -> {
                                l.info("Found the following connection IDs for tag <{}>: <{}>", tag, connectionIds);
                                return connectionIds;
                            })
                            .thenApply(connectionIds -> SudoRetrieveConnectionIdsByTagResponse.of(connectionIds,
                                    dittoHeaders));
            Patterns.pipe(retrieveConnectionIdsByTagResponseCompletionStage, getContext().dispatcher()).to(sender);
        } catch (final Exception e) {
            l.error(e, "Failed to load persistence ids from journal/snapshots for connections with tag <{}>.", tag);
            getSender().tell(buildErrorResponse(e, dittoHeaders), getSelf());
        }
    }

    private void getAllConnectionIDs(final WithDittoHeaders cmd) {
        final DittoDiagnosticLoggingAdapter logger = log.withCorrelationId(cmd);
        logger.info("Retrieving all connection IDs ...");
        try {
            final Source<String, NotUsed> idsFromSnapshots = persistenceIdsFromSnapshotSourceSupplier.get()
                    .via(Flow.fromFunction(result -> {
                        logger.debug("getIdsFromSnapshotsSource element: <{}>", result);
                        return result;
                    }))
                    .filter(ConnectionIdsRetrievalActor::snapshotIsNotDeleted)
                    .map(this::extractPersistenceIdFromDocument)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .via(Flow.fromFunction(result -> {
                        logger.debug("idsFromSnapshots element: <{}>", result);
                        return result;
                    }));
            final Source<String, NotUsed> idsFromJournal = persistenceIdsFromJournalSourceSupplier.get()
                    .via(Flow.fromFunction(result -> {
                        logger.debug("idsFromJournalSource element: <{}>", result);
                        return result;
                    }))
                    .filter(ConnectionIdsRetrievalActor::isNotDeleted)
                    .map(document -> document.getString(MongoReadJournal.J_EVENT_PID))
                    .via(Flow.fromFunction(result -> {
                        logger.debug("idsFromJournal element: <{}>", result);
                        return result;
                    }));

            final CompletionStage<List<String>> deletedPidsStage = persistenceIdsFromJournalSourceSupplier.get()
                    .filter(ConnectionIdsRetrievalActor::isDeleted)
                    .map(document -> document.getString(MongoReadJournal.J_EVENT_PID))
                    .runWith(Sink.seq(), materializer);

            final CompletionStage<CommandResponse> retrieveAllConnectionIdsResponse = deletedPidsStage
                    .thenApply(deletedIdsFromJournal -> {
                        logger.debug("deletedIdsFromJournal element: <{}>", deletedIdsFromJournal);
                        return deletedIdsFromJournal;
                    })
                    .thenCompose(deletedIdsFromJournal -> idsFromSnapshots.concat(idsFromJournal)
                            .filter(pid -> !deletedIdsFromJournal.contains(pid))
                            .filter(pid -> pid.startsWith(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX))
                            .map(pid -> pid.substring(
                                    ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX.length()))
                            .runWith(Sink.seq(), materializer)
                    )
                    .thenApply(idList -> idList.stream().sorted().toList())
                    .thenApply(LinkedHashSet::new)
                    .thenApply(ids -> buildResponse(cmd, ids))
                    .exceptionally(throwable -> buildErrorResponse(throwable, cmd.getDittoHeaders()));
            Patterns.pipe(retrieveAllConnectionIdsResponse, getContext().dispatcher()).to(getSender());
        } catch (final Exception e) {
            log.info("Failed to load persistence ids from journal/snapshots.", e);
            getSender().tell(buildErrorResponse(e, cmd.getDittoHeaders()), getSelf());
        }
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    private CommandResponse buildResponse(final WithDittoHeaders cmd, final Set<String> ids) {
        return RetrieveAllConnectionIdsResponse.of(ids, cmd.getDittoHeaders());
    }

    private ConnectivityErrorResponse buildErrorResponse(final Throwable throwable, final DittoHeaders dittoHeaders) {
        final ConnectivityInternalErrorException dittoRuntimeException =
                ConnectivityInternalErrorException.newBuilder()
                        .message(() -> "Failed to load connections ids from persistence: " + throwable.getMessage())
                        .dittoHeaders(dittoHeaders)
                        .build();
        return ConnectivityErrorResponse.of(dittoRuntimeException);
    }

    private Optional<String> extractPersistenceIdFromDocument(final Document document) {
        return Optional.ofNullable(document.getString(PERSISTENCE_ID_FIELD));
    }
}
