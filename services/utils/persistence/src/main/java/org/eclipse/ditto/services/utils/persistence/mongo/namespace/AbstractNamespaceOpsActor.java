/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.Collection;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespace;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespaceResponse;
import org.eclipse.ditto.signals.commands.namespaces.QueryNamespaceEmptiness;
import org.eclipse.ditto.signals.commands.namespaces.QueryNamespaceEmptinessResponse;

import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;

/**
 * Superclass of actors operating on the persistence at the level of namespaces.
 * It subscribes to the commands {@link QueryNamespaceEmptiness} and {@link PurgeNamespace} from the pub-sub mediator
 * and carries them out. Instances of this actor should start as cluster singletons in order not to perform
 * identical operations multiple times on the database.
 */
public abstract class AbstractNamespaceOpsActor extends AbstractActor {

    /**
     * The actor's logger.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final NamespaceOps namespaceOps;
    private final ActorMaterializer materializer;

    /**
     * Create a new instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param db Database where the event journal, snapshot store and metadata are located.
     */
    protected AbstractNamespaceOpsActor(final ActorRef pubSubMediator, final MongoDatabase db) {
        this.pubSubMediator = pubSubMediator;
        namespaceOps = NamespaceOps.of(db);
        materializer = ActorMaterializer.create(getContext());
    }

    /**
     * @return Resource type this actor operates on.
     */
    protected abstract String resourceType();

    /**
     * Get all documents in a given namespace among all collections in the database.
     *
     * @param namespace the namespace.
     * @return collection-filter pairs that identify all documents in the namespace.
     */
    protected abstract Collection<NamespaceSelection> selectNamespace(final String namespace);

    @Override
    public void preStart() {
        subscribeForNamespaceCommands();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(QueryNamespaceEmptiness.class, this::queryNamespaceEmptiness)
                .match(PurgeNamespace.class, this::purgeNamespace)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::ignoreSubscribeAck)
                .matchAny(message -> log.warning("unhandled: <{}>", message))
                .build();
    }

    private void subscribeForNamespaceCommands() {
        final ActorRef self = getSelf();
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(QueryNamespaceEmptiness.TYPE, self), self);
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(PurgeNamespace.TYPE, self), self);
    }

    private void ignoreSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        // do nothing
    }

    private void queryNamespaceEmptiness(final QueryNamespaceEmptiness command) {
        final ActorRef sender = getSender();
        namespaceOps.areEmpty(selectNamespace(command.getNamespace()))
                .runWith(Sink.head(), materializer)
                .thenAccept(setEmptiness -> {
                    final QueryNamespaceEmptinessResponse response;
                    if (setEmptiness) {
                        response = QueryNamespaceEmptinessResponse.empty(command.getNamespace(), resourceType(),
                                command.getDittoHeaders());
                    } else {
                        response = QueryNamespaceEmptinessResponse.notEmpty(command.getNamespace(), resourceType(),
                                command.getDittoHeaders());
                    }
                    sender.tell(response, getSelf());
                })
                .exceptionally(error -> {
                    LogUtil.enhanceLogWithCorrelationId(log, command);
                    log.error(error, "Failed: QueryNamespaceEmptiness");
                    // Reply nothing in error case
                    return null;
                });
    }

    private void purgeNamespace(final PurgeNamespace purgeNamespace) {
        final ActorRef sender = getSender();
        LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
        final Collection<NamespaceSelection> namespaceSelections = selectNamespace(purgeNamespace.getNamespace());
        log.info("Running <{}>. Affected collections: <{}>", purgeNamespace, namespaceSelections);
        namespaceOps.purgeAll(selectNamespace(purgeNamespace.getNamespace()))
                .runWith(Sink.head(), materializer)
                .thenAccept(errors -> {
                    // send response to speed up purge workflow
                    final PurgeNamespaceResponse response;
                    if (errors.isEmpty()) {
                        response = PurgeNamespaceResponse.successful(purgeNamespace.getNamespace(), resourceType(),
                                purgeNamespace.getDittoHeaders());
                    } else {
                        LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
                        final String namespace = purgeNamespace.getNamespace();
                        errors.forEach(error -> log.error(error, "Error purging namespace <{}>", namespace));
                        response = PurgeNamespaceResponse.failed(namespace, resourceType(),
                                purgeNamespace.getDittoHeaders());
                    }
                    sender.tell(response, getSelf());
                })
                .exceptionally(error -> {
                    LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
                    log.error(error, "Failed: QueryNamespaceEmptiness");
                    // Reply nothing - DB errors were converted to stream elements and handled
                    return null;
                });
    }

}
