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
package org.eclipse.ditto.services.utils.persistence.mongo.ops;

import static java.util.Objects.requireNonNull;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.akka.LogUtil;
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
public abstract class AbstractOpsActor extends AbstractActor {

    /**
     * The actor's logger.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final String resourceType;
    @Nullable
    private final NamespaceOps namespaceOps;
    @Nullable
    private final EntitiesOps entitiesOps;
    private final ActorMaterializer materializer;
    private final List<Closeable> toCloseWhenStopped;

    /**
     * Create a new instance of this actor.
     *
     * @param pubSubMediator the pubSubMediator
     * @param resourceType the resource type
     * @param namespaceOps the {@link NamespaceOps}, maybe {@code null} when {@code entitiesOps} is not null
     * @param entitiesOps the {@link EntitiesOps}, maybe {@code null} when {@code namespaceOps} is not null
     * @param toCloseWhenStopped a list of {@link Closeable} which have to be closed when the actor is stopped
     */
    protected AbstractOpsActor(final ActorRef pubSubMediator, final String resourceType,
            @Nullable final NamespaceOps namespaceOps, @Nullable final EntitiesOps entitiesOps,
            final Collection<Closeable> toCloseWhenStopped) {
        this.pubSubMediator = requireNonNull(pubSubMediator);
        this.resourceType = requireNonNull(resourceType);
        if (namespaceOps == null && entitiesOps == null) {
            throw new IllegalArgumentException("At least one of namespaceOps or entitiesOps must be specified.");
        }
        this.namespaceOps = namespaceOps;
        this.entitiesOps = entitiesOps;
        this.toCloseWhenStopped = Collections.unmodifiableList(new ArrayList<>(requireNonNull(toCloseWhenStopped)));
        materializer = ActorMaterializer.create(getContext());
    }

    /**
     * Create a new instance of this actor.
     *
     * @param pubSubMediator the pubSubMediator
     * @param resourceType the resource type
     * @param namespaceOps the {@link NamespaceOps}, maybe {@code null} when {@code entitiesOps} is not null
     * @param entitiesOps the {@link EntitiesOps}, maybe {@code null} when {@code namespaceOps} is not null
     */
    protected AbstractOpsActor(final ActorRef pubSubMediator, final String resourceType,
            @Nullable final NamespaceOps namespaceOps, @Nullable final EntitiesOps entitiesOps) {
        this(pubSubMediator, resourceType, namespaceOps, entitiesOps, Collections.emptyList());
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
                log.warning("Failed to close: <{}>", e.getMessage());
            }
        });
        super.postStop();
    }

    private void subscribeForNamespaceCommands() {
        if (namespaceOps != null) {
            log.debug("Subscribing for  namespace commands");
            final ActorRef self = getSelf();
            final DistributedPubSubMediator.Subscribe subscribe =
                    new DistributedPubSubMediator.Subscribe(PurgeNamespace.TYPE, getSubscribeGroup(), self);
            pubSubMediator.tell(subscribe, self);
        }
    }

    private void subscribeForEntitiesCommands() {
        if (entitiesOps != null) {
            final ActorRef self = getSelf();
            final String topic = PurgeEntities.getTopic(resourceType);
            final DistributedPubSubMediator.Subscribe subscribe =
                    new DistributedPubSubMediator.Subscribe(topic, getSubscribeGroup(), self);

            log.debug("Subscribing for  entities commands on topic <{}>", topic);
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
        if (namespaceOps == null) {
            log.warning("Cannot handle namespace command: <{}>", purgeNamespace);
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
                        errors.forEach(error -> log.error(error, "Error purging namespace <{}>", namespace));
                        response = PurgeNamespaceResponse.failed(namespace, resourceType,
                                purgeNamespace.getDittoHeaders());
                    }
                    sender.tell(response, getSelf());
                    log.info("Successfully purged namespace <{}>", namespace);
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
        if (entitiesOps == null) {
            log.warning("Cannot handle entities command: <{}>", purgeEntities);
            return;
        }
        if (!resourceType.equals(purgeEntities.getEntityType())) {
            log.warning("Expected command with entityType <{}>, but got: <{}>", resourceType, purgeEntities);
            return;
        }

        LogUtil.enhanceLogWithCorrelationId(log, purgeEntities);
        log.info("Running <{}>.", purgeEntities);
        final String entityType = purgeEntities.getEntityType();
        final List<String> entityIds = purgeEntities.getEntityIds();
        final ActorRef sender = getSender();

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
                    sender.tell(response, getSelf());
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
        log.debug("Got subscribeAck <{}>", subscribeAck);
    }

}
