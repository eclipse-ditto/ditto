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
package org.eclipse.ditto.internal.utils.persistence.operations;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReason;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.api.common.purge.PurgeEntities;
import org.eclipse.ditto.base.api.common.purge.PurgeEntitiesResponse;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespace;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespaceResponse;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithShutdownBehavior;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.CoordinatedShutdown;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.SharedKillSwitch;
import akka.stream.javadsl.Sink;

/**
 * Superclass of actors operating on the persistence at the level of namespaces.
 * It subscribes to the commands from the pub-sub mediator and carries them out.
 * Instances of the same type of this actor (running on different nodes) should register with the same group in order
 * to make sure that only one of those actors runs the command on the database.
 */
public abstract class AbstractPersistenceOperationsActor extends AbstractActorWithShutdownBehavior {

    /**
     * The actor's logger.
     */
    protected final ThreadSafeDittoLoggingAdapter logger;

    private static final Throwable KILL_SWITCH_EXCEPTION =
            new IllegalStateException("Aborting persistence operations stream because of graceful shutdown.");

    private final ActorRef pubSubMediator;
    private final EntityType entityType;
    @Nullable private final NamespacePersistenceOperations namespaceOps;
    @Nullable private final EntityPersistenceOperations entitiesOps;
    private final Materializer materializer;
    private final Collection<Closeable> toCloseWhenStopped;
    private final Duration delayAfterPersistenceActorShutdown;

    private final Map<Command<?>, ActorRef> lastCommandsAndSender;
    private final SharedKillSwitch killSwitch = KillSwitches.shared(getClass().getSimpleName());

    private AbstractPersistenceOperationsActor(final ActorRef pubSubMediator,
            final EntityType entityType,
            @Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final PersistenceOperationsConfig persistenceOperationsConfig,
            final Collection<Closeable> toCloseWhenStopped) {

        this.pubSubMediator = checkNotNull(pubSubMediator, "pub-sub mediator");
        this.entityType = checkNotNull(entityType, "entityType");
        if (namespaceOps == null && entitiesOps == null) {
            throw new IllegalArgumentException("At least one of namespaceOps or entitiesOps must be specified.");
        }
        this.namespaceOps = namespaceOps;
        this.entitiesOps = entitiesOps;
        this.toCloseWhenStopped = List.copyOf(toCloseWhenStopped);
        materializer = Materializer.createMaterializer(this::getContext);
        delayAfterPersistenceActorShutdown = persistenceOperationsConfig.getDelayAfterPersistenceActorShutdown();
        lastCommandsAndSender = new HashMap<>();
        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
    }

    /**
     * Constructs a new instance of this actor.
     *
     * @param pubSubMediator the pubSubMediator.
     * @param entityType the entity type.
     * @param namespaceOps the {@link NamespacePersistenceOperations}, maybe {@code null} when {@code entitiesOps} is
     * not {@code null}.
     * @param entitiesOps the {@link EntityPersistenceOperations}, maybe {@code null} when {@code namespaceOps} is not
     * {@code null}.
     * @param persistenceOperationsConfig the configuration settings of the persistence operations.
     * @param toCloseWhenStopped a list of {@link Closeable} which have to be closed when the actor is stopped.
     * @throws NullPointerException if {@code pubSubMediator}, {@code resourceType} or {@code toCloseWhenStopped} is
     * {@code null}.
     * @throws IllegalArgumentException if {@code namespaceOps} <em>and</em> {@code entitiesOps} is {@code null}.
     */
    protected AbstractPersistenceOperationsActor(final ActorRef pubSubMediator,
            final EntityType entityType,
            @Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final PersistenceOperationsConfig persistenceOperationsConfig,
            final Closeable toCloseWhenStopped,
            final Closeable... optionalToCloseWhenStopped) {

        this(pubSubMediator, entityType, namespaceOps, entitiesOps, persistenceOperationsConfig,
                toList(toCloseWhenStopped, optionalToCloseWhenStopped));
    }

    private static List<Closeable> toList(final Closeable toCloseWhenStopped,
            final Closeable... optionalToCloseWhenStopped) {

        checkNotNull(toCloseWhenStopped, "Closeable");
        checkNotNull(optionalToCloseWhenStopped, "optional Closeables");

        final List<Closeable> closeables = new ArrayList<>(1 + optionalToCloseWhenStopped.length);
        closeables.add(toCloseWhenStopped);
        Collections.addAll(closeables, optionalToCloseWhenStopped);

        return closeables;
    }

    /**
     * Constructs a new instance of this actor.
     *
     * @param pubSubMediator the pubSubMediator.
     * @param entityType the entity type.
     * @param namespaceOps the {@link NamespacePersistenceOperations}, maybe {@code null} when {@code entitiesOps} is
     * not {@code null}.
     * @param entitiesOps the {@link EntityPersistenceOperations}, maybe {@code null} when {@code namespaceOps} is not
     * {@code null}.
     * @param persistenceOperationsConfig the configuration settings of the persistence operations.
     * @throws NullPointerException if {@code pubSubMediator} or {@code resourceType} is {@code null}.
     * @throws IllegalArgumentException if {@code namespaceOps} <em>and</em> {@code entitiesOps} is {@code null}.
     */
    protected AbstractPersistenceOperationsActor(final ActorRef pubSubMediator,
            final EntityType entityType,
            @Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        this(pubSubMediator,
                entityType,
                namespaceOps,
                entitiesOps,
                persistenceOperationsConfig,
                Collections.emptyList());
    }

    protected abstract String getActorName();

    @Override
    public void preStart() {
        subscribeForNamespaceCommands();
        subscribeForEntitiesCommands();

        final var self = getSelf();
        final var coordinatedShutdown = CoordinatedShutdown.get(getContext().getSystem());
        final var serviceUnbindTask = "service-unbind-" + getActorName() ;
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind(), serviceUnbindTask,
                () -> Patterns.ask(getSelf(), Control.SERVICE_UNBIND, SHUTDOWN_ASK_TIMEOUT)
                        .thenApply(reply -> Done.done())
        );
        final var serviceRequestsDoneTask = "service-requests-done-" + getActorName();
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone(), serviceRequestsDoneTask,
                () -> Patterns.ask(self, Control.SERVICE_REQUESTS_DONE, SHUTDOWN_ASK_TIMEOUT)
                        .thenApply(reply -> Done.done())
        );
    }

    @Override
    public void postStop() throws Exception {
        toCloseWhenStopped.forEach(closeable -> {
            try {
                closeable.close();
            } catch (final IOException e) {
                logger.warning("Failed to close: <{}>!", e.getMessage());
            }
        });
        lastCommandsAndSender.clear();
        super.postStop();
    }

    private void subscribeForNamespaceCommands() {
        if (null != namespaceOps) {
            logger.debug("Subscribing for namespace commands.");
            final ActorRef self = getSelf();
            final DistributedPubSubMediator.Subscribe subscribe =
                    DistPubSubAccess.subscribeViaGroup(PurgeNamespace.TYPE, getSubscribeGroup(), self);
            pubSubMediator.tell(subscribe, self);
        }
    }

    private void subscribeForEntitiesCommands() {
        if (null != entitiesOps) {
            final ActorRef self = getSelf();
            final String topic = PurgeEntities.getTopic(entityType);
            final DistributedPubSubMediator.Subscribe subscribe =
                    DistPubSubAccess.subscribeViaGroup(topic, getSubscribeGroup(), self);

            logger.debug("Subscribing for entities commands on topic <{}>.", topic);
            pubSubMediator.tell(subscribe, self);
        }
    }

    private String getSubscribeGroup() {
        return getSelf().path().toStringWithoutAddress();
    }

    @Override
    public Receive handleMessage() {
        return ReceiveBuilder.create()
                .match(PurgeNamespace.class, this::purgeNamespace)
                .match(PurgeEntities.class, this::purgeEntities)
                .match(OpComplete.class, this::opComplete)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscribeAck)
                .matchAny(message -> logger.warning("Unhandled message: <{}>", message))
                .build();
    }

    @Override
    public void serviceUnbind(final Control serviceUnbind) {
        logger.info("{}: unsubscribing from pubsub for {} actor", serviceUnbind, getActorName());

        final ActorRef self = getSelf();
        final CompletableFuture<Object> unsubscribeFromPurgeNamespace;
        final CompletableFuture<Object> unsubscribeFromPurgeEntities;
        if (null != namespaceOps) {
            unsubscribeFromPurgeNamespace = Patterns.ask(pubSubMediator,
                            DistPubSubAccess.unsubscribeViaGroup(PurgeNamespace.TYPE, getSubscribeGroup(), self),
                            SHUTDOWN_ASK_TIMEOUT)
                    .toCompletableFuture();
        } else {
            unsubscribeFromPurgeNamespace = CompletableFuture.completedFuture(Done.getInstance());
        }

        if (null != entitiesOps) {
            unsubscribeFromPurgeEntities = Patterns.ask(pubSubMediator,
                            DistPubSubAccess.unsubscribeViaGroup(PurgeEntities.getTopic(entityType), getSubscribeGroup(),
                                    self),
                            SHUTDOWN_ASK_TIMEOUT)
                    .toCompletableFuture();
        } else {
            unsubscribeFromPurgeEntities = CompletableFuture.completedFuture(Done.getInstance());
        }

        final CompletableFuture<Done> unsubscribeTask =
                CompletableFuture.allOf(unsubscribeFromPurgeNamespace, unsubscribeFromPurgeEntities)
                        .thenApply(ack -> {
                            logger.info("Unsubscribed successfully from pubsub for {} actor", getActorName());

                            return Done.getInstance();
                        });

        Patterns.pipe(unsubscribeTask, getContext().getDispatcher()).to(getSender());
    }

    @Override
    public void serviceRequestsDone(final Control serviceRequestsDone) {
        logger.info("Re-schedule/Publish <{}> commands for <{}> via PubSub.", lastCommandsAndSender.size(),
                getActorName());

        killSwitch.abort(KILL_SWITCH_EXCEPTION);
        lastCommandsAndSender.forEach((command, sender) -> {
            if (command instanceof PurgeNamespace purgeNamespace) {
                pubSubMediator.tell(DistPubSubAccess.publishViaGroup(purgeNamespace.getType(), purgeNamespace), sender);
            } else if (command instanceof PurgeEntities purgeEntities) {
                final String topic = PurgeEntities.getTopic(entityType);
                final PurgeEntities purgeEntitiesCommand = PurgeEntities.of(entityType, purgeEntities.getEntityIds(),
                        purgeEntities.getDittoHeaders());
                pubSubMediator.tell(DistPubSubAccess.publishViaGroup(topic, purgeEntitiesCommand), sender);
            }
        });

        getSender().tell(Done.getInstance(), getSelf());
    }

    private void purgeNamespace(final PurgeNamespace purgeNamespace) {
        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(purgeNamespace);
        if (null == namespaceOps) {
            l.warning("Cannot handle namespace command: <{}>!", purgeNamespace);
            return;
        }

        rememberCommandAndSender(purgeNamespace, getSender());
        l.info("Running <{}>.", purgeNamespace);
        final String namespace = purgeNamespace.getNamespace();
        final ActorRef sender = getSender();

        namespaceOps.purge(purgeNamespace.getNamespace())
                .via(killSwitch.flow())
                .runWith(Sink.head(), materializer)
                .thenAccept(errors -> {
                    final PurgeNamespaceResponse response;
                    if (errors.isEmpty()) {
                        l.info("Successfully purged namespace <{}>.", namespace);
                        response = PurgeNamespaceResponse.successful(namespace, entityType,
                                purgeNamespace.getDittoHeaders());
                    } else {
                        errors.forEach(error -> l.error(error, "Error purging namespace <{}>!", namespace));
                        response = PurgeNamespaceResponse.failed(namespace, entityType,
                                purgeNamespace.getDittoHeaders());
                    }
                    sender.tell(response, getSelf());
                    getSelf().tell(new OpComplete(purgeNamespace, sender), ActorRef.noSender());
                })
                .exceptionally(error -> {
                    // Reply nothing - Error should not occur (DB errors were converted to stream elements and handled)
                    l.error(error, "Unexpected error when purging namespace <{}>!",
                            purgeNamespace.getNamespace());
                    getSelf().tell(new OpComplete(purgeNamespace, sender), ActorRef.noSender());

                    return null;
                });
    }

    private void purgeEntities(final PurgeEntities purgeEntities) {
        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(purgeEntities);
        if (null == entitiesOps) {
            l.warning("Cannot handle entities command: <{}>.", purgeEntities);
            return;
        }
        if (!entityType.equals(purgeEntities.getEntityType())) {
            l.warning("Expected command with entityType <{}>, but got: <{}>.", entityType, purgeEntities);
            return;
        }

        rememberCommandAndSender(purgeEntities, getSender());
        shutDownPersistenceActorsOfEntitiesToPurge(purgeEntities);
        schedulePurgingEntitiesIn(delayAfterPersistenceActorShutdown, purgeEntities);
    }

    private void shutDownPersistenceActorsOfEntitiesToPurge(final PurgeEntities purgeEntities) {
        final ShutdownReason reason = ShutdownReasonFactory.getPurgeEntitiesReason(purgeEntities.getEntityIds());
        final Shutdown shutdown = Shutdown.getInstance(reason, purgeEntities.getDittoHeaders());
        pubSubMediator.tell(DistPubSubAccess.publish(shutdown.getType(), shutdown), getSelf());
    }

    private void schedulePurgingEntitiesIn(final Duration delay, final PurgeEntities purgeEntities) {
        final ActorRef initiator = getSender();
        getContext()
                .system()
                .scheduler()
                .scheduleOnce(delay, () -> doPurgeEntities(purgeEntities, initiator), getContext().dispatcher());
    }

    private void doPurgeEntities(final PurgeEntities purgeEntities, final ActorRef initiator) {
        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(purgeEntities);
        if (null == entitiesOps) {
            l.warning("Cannot handle entities command: <{}>", purgeEntities);
            return;
        }

        l.info("Running <{}>.", purgeEntities);
        final EntityType purgeEntityType = purgeEntities.getEntityType();
        final List<EntityId> entityIds = purgeEntities.getEntityIds();

        entitiesOps.purgeEntities(purgeEntities.getEntityIds())
                .via(killSwitch.flow())
                .runWith(Sink.head(), materializer)
                .thenAccept(errors -> {
                    final PurgeEntitiesResponse response;
                    if (errors.isEmpty()) {
                        l.info("Successfully purged entities of type <{}>: <{}>", purgeEntityType, entityIds);
                        response = PurgeEntitiesResponse.successful(purgeEntityType, purgeEntities.getDittoHeaders());
                    } else {
                        errors.forEach(error -> l.error(error, "Error purging entities of type <{}>: <{}>",
                                purgeEntityType, entityIds));
                        response = PurgeEntitiesResponse.failed(purgeEntityType, purgeEntities.getDittoHeaders());
                    }
                    initiator.tell(response, getSelf());
                    getSelf().tell(new OpComplete(purgeEntities, initiator), ActorRef.noSender());
                })
                .exceptionally(error -> {
                    // Reply nothing - Error should not occur (DB errors were converted to stream elements and handled)
                    l.error(error, "Unexpected error when purging entities <{}>!", purgeEntities.getEntityIds());
                    getSelf().tell(new OpComplete(purgeEntities, initiator), ActorRef.noSender());

                    return null;
                });
    }

    private void rememberCommandAndSender(final Command<?> command, final ActorRef sender) {
        lastCommandsAndSender.put(command, sender);
    }

    private void opComplete(final OpComplete opComplete) {
        logger.debug("Operation complete remove lastCommand {} and sender {} from map.", opComplete.command,
                opComplete.sender);
        lastCommandsAndSender.remove(opComplete.command, opComplete.sender);
    }

    private void handleSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        logger.debug("Got subscribeAck <{}>.", subscribeAck);
    }

    public record OpComplete(Command<?> command, ActorRef sender) {}

}
