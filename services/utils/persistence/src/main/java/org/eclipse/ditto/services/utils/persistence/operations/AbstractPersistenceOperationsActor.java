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
package org.eclipse.ditto.services.utils.persistence.operations;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.common.Shutdown;
import org.eclipse.ditto.signals.commands.common.ShutdownReason;
import org.eclipse.ditto.signals.commands.common.ShutdownReasonFactory;
import org.eclipse.ditto.signals.commands.common.purge.PurgeEntities;
import org.eclipse.ditto.signals.commands.common.purge.PurgeEntitiesResponse;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespace;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespaceResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;

/**
 * Superclass of actors operating on the persistence at the level of namespaces.
 * It subscribes to the commands from the pub-sub mediator and carries them out.
 * Instances of the same type of this actor (running on different nodes) should register with the same group in order
 * to make sure that only one of those actors runs the command on the database.
 */
public abstract class AbstractPersistenceOperationsActor extends AbstractActor {

    /**
     * The actor's logger.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final String resourceType;
    @Nullable private final NamespacePersistenceOperations namespaceOps;
    @Nullable private final EntityPersistenceOperations entitiesOps;
    private final ActorMaterializer materializer;
    private final Collection<Closeable> toCloseWhenStopped;

    private final Duration delayAfterPersistenceActorShutdown;

    private AbstractPersistenceOperationsActor(final ActorRef pubSubMediator,
            final String resourceType,
            @Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final PersistenceOperationsConfig persistenceOperationsConfig,
            final Collection<Closeable> toCloseWhenStopped) {

        this.pubSubMediator = checkNotNull(pubSubMediator, "pub-sub mediator");
        this.resourceType = checkNotNull(resourceType, "resource type");
        if (namespaceOps == null && entitiesOps == null) {
            throw new IllegalArgumentException("At least one of namespaceOps or entitiesOps must be specified.");
        }
        this.namespaceOps = namespaceOps;
        this.entitiesOps = entitiesOps;
        this.toCloseWhenStopped = Collections.unmodifiableCollection(toCloseWhenStopped);
        materializer = ActorMaterializer.create(getContext());
        delayAfterPersistenceActorShutdown = persistenceOperationsConfig.getDelayAfterPersistenceActorShutdown();
    }

    /**
     * Constructs a new instance of this actor.
     *
     * @param pubSubMediator the pubSubMediator.
     * @param resourceType the resource type.
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
            final String resourceType,
            @Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final PersistenceOperationsConfig persistenceOperationsConfig,
            final Closeable toCloseWhenStopped,
            final Closeable ... optionalToCloseWhenStopped) {

        this(pubSubMediator, resourceType, namespaceOps, entitiesOps, persistenceOperationsConfig,
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
     * @param pubSubMediator the pubSubMediator
     * @param resourceType the resource type
     * @param namespaceOps the {@link NamespacePersistenceOperations}, maybe {@code null} when {@code entitiesOps} is
     * not {@code null}.
     * @param entitiesOps the {@link EntityPersistenceOperations}, maybe {@code null} when {@code namespaceOps} is not
     * {@code null}.
     * @param persistenceOperationsConfig the configuration settings of the persistence operations.
     * @throws NullPointerException if {@code pubSubMediator} or {@code resourceType} is {@code null}.
     * @throws IllegalArgumentException if {@code namespaceOps} <em>and</em> {@code entitiesOps} is {@code null}.
     */
    protected AbstractPersistenceOperationsActor(final ActorRef pubSubMediator,
            final String resourceType,
            @Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        this(pubSubMediator,
                resourceType,
                namespaceOps,
                entitiesOps,
                persistenceOperationsConfig,
                Collections.emptyList());
    }

    @Override
    public void preStart() {
        subscribeForNamespaceCommands();
        subscribeForEntitiesCommands();
    }

    @Override
    public void postStop() throws Exception {
        toCloseWhenStopped.forEach(closeable -> {
            try {
                closeable.close();
            } catch (final IOException e) {
                log.warning("Failed to close: <{}>!", e.getMessage());
            }
        });
        super.postStop();
    }

    private void subscribeForNamespaceCommands() {
        if (null != namespaceOps) {
            log.debug("Subscribing for namespace commands.");
            final ActorRef self = getSelf();
            final DistributedPubSubMediator.Subscribe subscribe =
                    new DistributedPubSubMediator.Subscribe(PurgeNamespace.TYPE, getSubscribeGroup(), self);
            pubSubMediator.tell(subscribe, self);
        }
    }

    private void subscribeForEntitiesCommands() {
        if (null != entitiesOps) {
            final ActorRef self = getSelf();
            final String topic = PurgeEntities.getTopic(resourceType);
            final DistributedPubSubMediator.Subscribe subscribe =
                    new DistributedPubSubMediator.Subscribe(topic, getSubscribeGroup(), self);

            log.debug("Subscribing for  entities commands on topic <{}>.", topic);
            pubSubMediator.tell(subscribe, self);
        }
    }

    private String getSubscribeGroup() {
        return getSelf().path().toStringWithoutAddress();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(PurgeNamespace.class, this::purgeNamespace)
                .match(PurgeEntities.class, this::purgeEntities)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscribeAck)
                .matchAny(message -> log.warning("unhandled: <{}>", message))
                .build();
    }

    private void purgeNamespace(final PurgeNamespace purgeNamespace) {
        if (null == namespaceOps) {
            log.warning("Cannot handle namespace command: <{}>!", purgeNamespace);
            return;
        }

        LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
        log.info("Running <{}>.", purgeNamespace);
        final String namespace = purgeNamespace.getNamespace();
        final ActorRef sender = getSender();

        namespaceOps.purge(purgeNamespace.getNamespace())
                .runWith(Sink.head(), materializer)
                .thenAccept(errors -> {
                    final PurgeNamespaceResponse response;
                    if (errors.isEmpty()) {
                        response = PurgeNamespaceResponse.successful(namespace, resourceType,
                                purgeNamespace.getDittoHeaders());
                    } else {
                        LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
                        errors.forEach(error -> log.error(error, "Error purging namespace <{}>!", namespace));
                        response = PurgeNamespaceResponse.failed(namespace, resourceType,
                                purgeNamespace.getDittoHeaders());
                    }
                    sender.tell(response, getSelf());
                    log.info("Successfully purged namespace <{}>.", namespace);
                })
                .exceptionally(error -> {
                    LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
                    // Reply nothing - Error should not occur (DB errors were converted to stream elements and handled)
                    log.error(error, "Unexpected error when purging namespace <{}>!",
                            purgeNamespace.getNamespace());
                    return null;
                });
    }

    private void purgeEntities(final PurgeEntities purgeEntities) {

        if (null == entitiesOps) {
            log.warning("Cannot handle entities command: <{}>.", purgeEntities);
            return;
        }
        if (!resourceType.equals(purgeEntities.getEntityType())) {
            log.warning("Expected command with entityType <{}>, but got: <{}>.", resourceType, purgeEntities);
            return;
        }

        shutDownPersistenceActorsOfEntitiesToPurge(purgeEntities);
        schedulePurgingEntitiesIn(delayAfterPersistenceActorShutdown, purgeEntities);
    }

    private void shutDownPersistenceActorsOfEntitiesToPurge(final PurgeEntities purgeEntities) {
        final ShutdownReason reason = ShutdownReasonFactory.getPurgeEntitiesReason(purgeEntities.getEntityIds());
        final Shutdown shutdown = Shutdown.getInstance(reason, purgeEntities.getDittoHeaders());
        pubSubMediator.tell(new DistributedPubSubMediator.Publish(shutdown.getType(), shutdown), getSelf());
    }

    private void schedulePurgingEntitiesIn(final Duration delay, final PurgeEntities purgeEntities) {
        final ActorRef initiator = getSender();
        getContext()
                .system()
                .scheduler()
                .scheduleOnce(delay, () -> doPurgeEntities(purgeEntities, initiator), getContext().dispatcher());
    }

    private void doPurgeEntities(final PurgeEntities purgeEntities, final ActorRef initiator) {
        if (null == entitiesOps) {
            log.warning("Cannot handle entities command: <{}>", purgeEntities);
            return;
        }

        LogUtil.enhanceLogWithCorrelationId(log, purgeEntities);
        log.info("Running <{}>.", purgeEntities);
        final String entityType = purgeEntities.getEntityType();
        final List<String> entityIds = purgeEntities.getEntityIds();

        entitiesOps.purgeEntities(purgeEntities.getEntityIds())
                .runWith(Sink.head(), materializer)
                .thenAccept(errors -> {
                    final PurgeEntitiesResponse response;
                    if (errors.isEmpty()) {
                        response = PurgeEntitiesResponse.successful(entityType, purgeEntities.getDittoHeaders());
                    } else {
                        LogUtil.enhanceLogWithCorrelationId(log, purgeEntities);
                        errors.forEach(error -> log.error(error, "Error purging entities of type <{}>: <{}>",
                                entityType, entityIds));
                        response = PurgeEntitiesResponse.failed(purgeEntities.getEntityType(),
                                purgeEntities.getDittoHeaders());
                    }
                    initiator.tell(response, getSelf());
                    log.info("Successfully purged entities of type <{}>: <{}>", entityType, entityIds);
                })
                .exceptionally(error -> {
                    LogUtil.enhanceLogWithCorrelationId(log, purgeEntities);
                    // Reply nothing - Error should not occur (DB errors were converted to stream elements and handled)
                    log.error(error, "Unexpected error when purging entities <{}>!",
                            purgeEntities.getEntityIds());
                    return null;
                });
    }

    private void handleSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.debug("Got subscribeAck <{}>.", subscribeAck);
    }

}
