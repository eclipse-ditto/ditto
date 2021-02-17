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
package org.eclipse.ditto.services.connectivity.messaging;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.bson.Document;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityInternalErrorException;
import org.eclipse.ditto.services.connectivity.config.ConnectionIdsRetrievalConfig;
import org.eclipse.ditto.services.connectivity.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityErrorResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveAllConnectionIds;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveAllConnectionIdsResponse;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor handling messages related to connections e.g. retrieving all connections ids.
 */
public final class ConnectionIdsRetrievalActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "connectionIdsRetrieval";

    private static final String PERSISTENCE_ID_FIELD = "_id";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Supplier<Source<String, NotUsed>> persistenceIdsFromJournalSourceSupplier;
    private final Supplier<Source<Document, NotUsed>> persistenceIdsFromSnapshotSourceSupplier;
    private final Materializer materializer;
    private final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig;

    @SuppressWarnings("unused")
    private ConnectionIdsRetrievalActor(
            final Supplier<Source<String, NotUsed>> persistenceIdsFromJournalSourceSupplier,
            final Supplier<Source<Document, NotUsed>> persistenceIdsFromSnapshotSourceSupplier) {
        this.persistenceIdsFromJournalSourceSupplier = persistenceIdsFromJournalSourceSupplier;
        this.persistenceIdsFromSnapshotSourceSupplier = persistenceIdsFromSnapshotSourceSupplier;
        materializer = Materializer.createMaterializer(this::getContext);
        connectionIdsRetrievalConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(getContext().system().settings().config()))
                        .getConnectionIdsRetrievalConfig();
    }

    @SuppressWarnings("unused")
    private ConnectionIdsRetrievalActor(final MongoReadJournal readJournal) {
        materializer = Materializer.createMaterializer(this::getContext);
        connectionIdsRetrievalConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(getContext().system().settings().config()))
                        .getConnectionIdsRetrievalConfig();
        persistenceIdsFromJournalSourceSupplier =
                () -> readJournal.getJournalPids(connectionIdsRetrievalConfig.getReadJournalBatchSize(),
                        Duration.ofSeconds(1), materializer);
        persistenceIdsFromSnapshotSourceSupplier =
                () -> readJournal.getNewestSnapshotsAbove("", connectionIdsRetrievalConfig.getReadSnapshotBatchSize(),
                        materializer);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param readJournal readJournal to extract current PIDs from.
     * @return the Akka configuration Props object.
     */
    public static Props props(final MongoReadJournal readJournal) {
        return Props.create(ConnectionIdsRetrievalActor.class, readJournal);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param persistenceIdsFromJournalSourceSupplier supplier of persistence ids source from journal
     * @param persistenceIdsFromSnapshotSourceSupplier supplier of persistence ids source from snapshot
     * @return the Akka configuration Props object.
     */
    static Props props(
            final Supplier<Source<String, NotUsed>> persistenceIdsFromJournalSourceSupplier,
            final Supplier<Source<Document, NotUsed>> persistenceIdsFromSnapshotSourceSupplier) {
        return Props.create(ConnectionIdsRetrievalActor.class, persistenceIdsFromJournalSourceSupplier,
                persistenceIdsFromSnapshotSourceSupplier);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveAllConnectionIds.class, this::getAllConnectionIDs)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void getAllConnectionIDs(final WithDittoHeaders<RetrieveAllConnectionIds> cmd) {
        try {
            final Source<String, NotUsed> idsFromSnapshots = getIdsFromSnapshotsSource();
            final Source<String, NotUsed> idsFromJournal = persistenceIdsFromJournalSourceSupplier.get();
            final CompletionStage<CommandResponse> retrieveAllConnectionIdsResponse =
                    idsFromSnapshots.concat(idsFromJournal)
                            .filter(pid -> pid.startsWith(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX))
                            .map(pid -> pid.substring(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX.length()))
                            .runWith(Sink.seq(), materializer)
                            .thenApply(HashSet::new)
                            .thenApply(ids -> buildResponse(cmd, ids))
                            .exceptionally(throwable -> buildErrorResponse(throwable, cmd.getDittoHeaders()));
            Patterns.pipe(retrieveAllConnectionIdsResponse, getContext().dispatcher()).to(getSender());
        } catch (final Exception e) {
            log.info("Failed to load persistence ids from journal/snapshots.", e);
            getSender().tell(buildErrorResponse(e, cmd.getDittoHeaders()), getSelf());
        }
    }

    private Source<String, NotUsed> getIdsFromSnapshotsSource() {
        return persistenceIdsFromSnapshotSourceSupplier.get()
                .map(this::extractPersistenceIdFromDocument)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private CommandResponse buildResponse(final WithDittoHeaders<RetrieveAllConnectionIds> cmd,
            final Set<String> ids) {
        return RetrieveAllConnectionIdsResponse.of(ids, cmd.getDittoHeaders());
    }

    private CommandResponse buildErrorResponse(final Throwable throwable, final DittoHeaders dittoHeaders) {
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
