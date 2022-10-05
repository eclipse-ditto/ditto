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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithShutdownBehavior;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.SnapshotFilter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.SharedKillSwitch;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;


/**
 * An actor that streams from the snapshot store of a service with Mongo persistence plugin on request.
 */
@AllValuesAreNonnullByDefault
public final class SnapshotStreamingActor extends AbstractActorWithShutdownBehavior {

    /**
     * The name of the snapshot streaming actor.
     */
    public static final String ACTOR_NAME = "snapshotStreamingActor";

    private static final Exception KILL_SWITCH_EXCEPTION =
            new IllegalStateException("Abort streaming of snapshots because of graceful shutdown.");

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final Materializer materializer = Materializer.createMaterializer(this::getContext);
    private final SharedKillSwitch killSwitch = KillSwitches.shared(ACTOR_NAME);

    private final Function<String, EntityId> pid2EntityId;
    private final Function<EntityId, String> entityId2Pid;
    private final DittoMongoClient mongoClient;
    private final MongoReadJournal readJournal;
    private final ActorRef pubSubMediator;


    @SuppressWarnings("unused") // called by reflection
    private SnapshotStreamingActor(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoMongoClient mongoClient,
            final MongoReadJournal readJournal,
            final ActorRef pubSubMediator) {
        this.pid2EntityId = pid2EntityId;
        this.entityId2Pid = entityId2Pid;
        this.mongoClient = mongoClient;
        this.readJournal = readJournal;
        this.pubSubMediator = pubSubMediator;
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
        pubSubMediator = DistributedPubSub.get(getContext().getSystem()).mediator();
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
     * @param pubSubMediator the pubSubMediator.
     * @return Props for this actor.
     */
    public static Props propsForTest(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoMongoClient mongoClient,
            final MongoReadJournal readJournal,
            final ActorRef pubSubMediator) {

        return Props.create(SnapshotStreamingActor.class, pid2EntityId, entityId2Pid, mongoClient, readJournal,
                pubSubMediator);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        final var self = getSelf();
        pubSubMediator.tell(DistPubSubAccess.subscribeViaGroup(SudoStreamSnapshots.TYPE, ACTOR_NAME, self), self);

        final var coordinatedShutdown = CoordinatedShutdown.get(getContext().getSystem());
        final var serviceUnbindTask = "service-unbind-" + ACTOR_NAME;
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind(), serviceUnbindTask,
                () -> Patterns.ask(self, Control.SERVICE_UNBIND, SHUTDOWN_ASK_TIMEOUT)
                        .thenApply(reply -> Done.done())
        );

        final var serviceRequestsDoneTask = "service-requests-done-" + ACTOR_NAME;
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone(), serviceRequestsDoneTask,
                () -> Patterns.ask(self, Control.SERVICE_REQUESTS_DONE, SHUTDOWN_ASK_TIMEOUT)
                        .thenApply(reply -> Done.done())
        );
    }

    @Override
    public void postStop() throws Exception {
        mongoClient.close();
        super.postStop();
    }

    @Override
    public Receive handleMessage() {
        return ReceiveBuilder.create()
                .match(SudoStreamSnapshots.class, this::startStreaming)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscribeAck)
                .matchAny(message -> log.warning("Unexpected message: <{}>", message))
                .build();
    }

    @Override
    public void serviceUnbind(final Control serviceUnbind) {
        log.info("{}: unsubscribing from pubsub for {} actor", serviceUnbind, ACTOR_NAME);

        final CompletableFuture<Done> unsubscribeTask = Patterns.ask(pubSubMediator,
                        DistPubSubAccess.unsubscribeViaGroup(SudoStreamSnapshots.TYPE, ACTOR_NAME,
                                getSelf()), SHUTDOWN_ASK_TIMEOUT)
                .toCompletableFuture()
                .thenApply(ack -> {
                    log.info("Unsubscribed successfully from pubsub for {} actor", ACTOR_NAME);
                    return Done.getInstance();
                });

        Patterns.pipe(unsubscribeTask, getContext().getDispatcher()).to(getSender());
    }

    @Override
    public void serviceRequestsDone(final Control serviceRequestsDone) {
        log.info("Abort streaming of snapshots because of graceful shutdown.");
        killSwitch.abort(KILL_SWITCH_EXCEPTION);
        getSender().tell(Done.getInstance(), getSelf());
    }

    private void handleSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.info("Successfully subscribed to distributed pub/sub on topic <{}> for group <{}>.",
                subscribeAck.subscribe().topic(), subscribeAck.subscribe().group());
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
                .via(killSwitch.flow())
                .initialTimeout(timeout)
                .idleTimeout(timeout)
                .runWith(StreamRefs.sourceRef(), materializer);
        getSender().tell(sourceRef, getSelf());
    }

}
