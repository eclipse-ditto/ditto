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
package org.eclipse.ditto.internal.utils.persistence.api.streaming;

import java.io.Closeable;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.pubsub.DistributedPubSub;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SharedKillSwitch;
import org.apache.pekko.stream.SourceRef;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamRefs;
import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.pekko.actors.AbstractActorWithShutdownBehavior;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotFilter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;


/**
 * An actor that streams from the snapshot store of a service on request.
 * <p>
 * The actor is backend-neutral: it depends only on the {@link DittoReadJournal} abstraction (for
 * {@link DittoReadJournal#getNewestSnapshotsAbove(SnapshotFilter, int, Materializer, String...)}) and on an injected
 * {@link Closeable} resource that it closes on {@code postStop}. Each persistence backend supplies the concrete read
 * journal and the resource to close (MongoDB passes its client; PostgreSQL passes a no-op because its connection pool is
 * owned elsewhere).
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
    private final DittoReadJournal readJournal;
    private final Closeable resourceToClose;
    private final ActorRef pubSubMediator;


    @SuppressWarnings("unused") // called by reflection
    private SnapshotStreamingActor(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoReadJournal readJournal,
            final Closeable resourceToClose,
            final ActorRef pubSubMediator) {
        this.pid2EntityId = pid2EntityId;
        this.entityId2Pid = entityId2Pid;
        this.readJournal = readJournal;
        this.resourceToClose = resourceToClose;
        this.pubSubMediator = pubSubMediator;
    }

    @SuppressWarnings("unused") // called by reflection
    private SnapshotStreamingActor(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoReadJournal readJournal,
            final Closeable resourceToClose) {
        this.pid2EntityId = pid2EntityId;
        this.entityId2Pid = entityId2Pid;
        this.readJournal = readJournal;
        this.resourceToClose = resourceToClose;
        this.pubSubMediator = DistributedPubSub.get(getContext().getSystem()).mediator();
    }

    /**
     * Create Pekko Props object for this actor.
     * <p>
     * The caller (the active persistence backend's {@code streaming(...)} accessor) supplies the backend's
     * {@link DittoReadJournal} and the {@link Closeable} resource the actor closes on stop.
     *
     * @param pid2EntityId function mapping PID to entity ID.
     * @param entityId2Pid function mapping entity ID to PID.
     * @param readJournal the backend's read journal.
     * @param resourceToClose the backend resource to close when the actor stops (a no-op {@code Closeable} when the
     * backend owns its resources elsewhere).
     * @return Props for this actor.
     */
    public static Props props(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoReadJournal readJournal,
            final Closeable resourceToClose) {

        return Props.create(SnapshotStreamingActor.class, pid2EntityId, entityId2Pid, readJournal, resourceToClose);
    }

    /**
     * Create Pekko Props object for this actor with given read journal, resource and pubSubMediator.
     * This is useful for unit tests with a mocked read journal.
     *
     * @param pid2EntityId function mapping PID to entity ID.
     * @param entityId2Pid function mapping entity ID to PID.
     * @param readJournal the read journal.
     * @param resourceToClose the resource to close when the actor stops.
     * @param pubSubMediator the pubSubMediator.
     * @return Props for this actor.
     */
    public static Props propsForTest(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final DittoReadJournal readJournal,
            final Closeable resourceToClose,
            final ActorRef pubSubMediator) {

        return Props.create(SnapshotStreamingActor.class, pid2EntityId, entityId2Pid, readJournal, resourceToClose,
                pubSubMediator);
    }

    /**
     * The per-incarnation resource pair for backends whose streaming resources are STATEFUL (Mongo: a
     * dedicated client the actor owns and closes). Pekko's default {@code preRestart} runs {@code postStop},
     * which closes {@code resourceToClose}; arg-captured Props would hand the CLOSED resource to the next
     * incarnation. The supplier-based {@link #props(Function, Function, Supplier)} rebuilds fresh resources
     * per incarnation instead.
     */
    public record StreamingResources(DittoReadJournal readJournal, Closeable resourceToClose) {}

    @SuppressWarnings("unused") // called by reflection
    private SnapshotStreamingActor(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final Supplier<StreamingResources> resourcesPerIncarnation) {
        final StreamingResources resources = resourcesPerIncarnation.get();
        this.pid2EntityId = pid2EntityId;
        this.entityId2Pid = entityId2Pid;
        this.readJournal = resources.readJournal();
        this.resourceToClose = resources.resourceToClose();
        this.pubSubMediator = DistributedPubSub.get(getContext().getSystem()).mediator();
    }

    /**
     * Props whose resources are rebuilt for EVERY incarnation (supervised restarts included). Backends whose
     * streaming actor owns a closeable client (Mongo) MUST use this overload; backends with stateless/shared
     * resources (Postgres: shared read journal + NoOpCloseable) may keep the instance-capturing overload.
     *
     * @param pid2EntityId function mapping PID to entity ID.
     * @param entityId2Pid function mapping entity ID to PID.
     * @param resourcesPerIncarnation supplies a fresh {@link StreamingResources} for every actor incarnation.
     * @return Props for this actor.
     */
    public static Props props(final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid,
            final Supplier<StreamingResources> resourcesPerIncarnation) {
        return Props.create(SnapshotStreamingActor.class, pid2EntityId, entityId2Pid, resourcesPerIncarnation);
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
        resourceToClose.close();
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
        final Source<SnapshotEntry, NotUsed> snapshotSource = readJournal.getNewestSnapshotsAbove(
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

    private StreamedSnapshot mapSnapshot(final SnapshotEntry snapshot) {
        // the pid is exposed via the typed accessor; the neutral payload already excludes the pid field
        final EntityId entityId = pid2EntityId.apply(snapshot.getPid().orElseThrow());

        return StreamedSnapshot.of(entityId, snapshot.getJson());
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
