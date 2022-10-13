/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.SnapshotFilter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;

/**
 * An actor that streams from the snapshot store of a service with Mongo persistence plugin on request.
 */
@AllValuesAreNonnullByDefault
public final class SnapshotStreamingActor extends AbstractActor {

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final Materializer materializer = Materializer.createMaterializer(this::getContext);

    private final Function<String, EntityId> pid2EntityId;
    private final Function<EntityId, String> entityId2Pid;
    private final DittoMongoClient mongoClient;
    private final MongoReadJournal readJournal;

    @SuppressWarnings("unused") // called by reflection
    private SnapshotStreamingActor(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoMongoClient mongoClient,
            final MongoReadJournal readJournal) {
        this.pid2EntityId = pid2EntityId;
        this.entityId2Pid = entityId2Pid;
        this.mongoClient = mongoClient;
        this.readJournal = readJournal;
    }

    @SuppressWarnings("unused") // called by reflection
    private SnapshotStreamingActor(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid) {
        this.pid2EntityId = pid2EntityId;
        this.entityId2Pid = entityId2Pid;

        final var config = getContext().getSystem().settings().config();
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(config));
        mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        readJournal = MongoReadJournal.newInstance(config, mongoClient, getContext().getSystem());
    }

    /**
     * Create Akka Props object for this actor.
     *
     * @param pid2EntityId function mapping PID to entity ID.
     * @param entityId2Pid function mapping entity ID to PID.
     * @return Props for this actor.
     */
    public static Props props(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid) {

        return Props.create(SnapshotStreamingActor.class, pid2EntityId, entityId2Pid);
    }

    /**
     * Create Akka Props object for this actor with given Mongo client and read journal.
     * This is useful for unit tests with a mocked MongoDB.
     *
     * @param pid2EntityId function mapping PID to entity ID.
     * @param entityId2Pid function mapping entity ID to PID.
     * @param mongoClient MongoDB client.
     * @param readJournal the read journal.
     * @return Props for this actor.
     */
    public static Props propsForTest(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoMongoClient mongoClient,
            final MongoReadJournal readJournal) {

        return Props.create(SnapshotStreamingActor.class, pid2EntityId, entityId2Pid, mongoClient, readJournal);
    }

    @Override
    public void postStop() throws Exception {
        mongoClient.close();
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(SudoStreamSnapshots.class, this::startStreaming)
                .matchAny(message -> log.warning("Unexpected message: <{}>", message))
                .build();
    }

    private Source<StreamedSnapshot, NotUsed> createSource(final SudoStreamSnapshots command) {
        log.info("Starting stream for <{}>", command);
        final int batchSize = command.getBurst();
        final Source<Document, NotUsed> snapshotSource = readJournal.getNewestSnapshotsAbove(
                getSnapshotFilterFromCommand(command),
                batchSize,
                materializer,
                command.getSnapshotFields().stream().map(JsonValue::asString).toArray(String[]::new)
        );
        return snapshotSource.map(this::mapSnapshot).log("snapshot-streaming", log);
    }

    private SnapshotFilter getSnapshotFilterFromCommand(final SudoStreamSnapshots command) {
        final String start = command.hasNonEmptyLowerBound() ? entityId2Pid.apply(command.getLowerBound()) : "";
        final String pidFilter = FilteredNamespacedEntityId.toPidFilter(command, entityId2Pid);
        return SnapshotFilter.of(start, pidFilter);
    }

    /**
     * Implementation of NamespacedEntityId that generates an entity id filter based on a given set of namespaces.
     */
    private static class FilteredNamespacedEntityId extends AbstractNamespacedEntityId {

        private FilteredNamespacedEntityId(final EntityType type, final String namespaceRegex) {
            super(type, namespaceRegex, ".*", false);
        }

        /**
         * Creates a pid regex from the given SudoStreamSnapshots command.
         *
         * @param command the command from which to create the entity id filter
         * @param entityId2Pid the function to convert from entity if to persistence id
         * @return a regular expression that matches PIDs of the given namespace(s)
         */
        static String toPidFilter(final SudoStreamSnapshots command, final Function<EntityId, String> entityId2Pid) {
            return Optional.of(command.getNamespaces())
                    .filter(namespaces -> !namespaces.isEmpty())
                    .map(Collection::stream)
                    .map(namespaces -> namespaces.collect(Collectors.joining("|", "(", ")")))
                    .map(regex -> new FilteredNamespacedEntityId(EntityType.of(command.getType()), regex))
                    .map(entityId2Pid)
                    .map(pid -> "^" + pid)
                    .orElse("");
        }
    }

    private StreamedSnapshot mapSnapshot(final Document snapshot) {
        // _id is correct because documents were grouped by pid which results in having pid in _id
        final EntityId entityId = pid2EntityId.apply(snapshot.getString(MongoReadJournal.S_ID));
        snapshot.remove(MongoReadJournal.S_ID);
        final JsonObject snapshotJson = JsonObject.of(snapshot.toJson());
        return StreamedSnapshot.of(entityId, snapshotJson);
    }

    private void startStreaming(final SudoStreamSnapshots command) {
        final Duration timeout = Duration.ofMillis(command.getTimeoutMillis());
        final SourceRef<StreamedSnapshot> sourceRef = createSource(command)
                .initialTimeout(timeout)
                .idleTimeout(timeout)
                .runWith(StreamRefs.sourceRef(), materializer);
        getSender().tell(sourceRef, getSelf());
    }
}
