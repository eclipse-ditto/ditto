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
package org.eclipse.ditto.internal.utils.namespaces;

import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.namespaces.signals.commands.BlockNamespace;
import org.eclipse.ditto.base.model.namespaces.signals.commands.BlockNamespaceResponse;
import org.eclipse.ditto.base.model.namespaces.signals.commands.UnblockNamespace;
import org.eclipse.ditto.base.model.namespaces.signals.commands.UnblockNamespaceResponse;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which updates the distributed cache of blocked namespaces.
 */
public final class BlockedNamespacesUpdater extends AbstractActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "blockedNamespacesUpdater";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final BlockedNamespaces blockedNamespaces;

    @SuppressWarnings("unused")
    private BlockedNamespacesUpdater(final BlockedNamespaces blockedNamespaces, final ActorRef pubSubMediator) {
        this.blockedNamespaces = blockedNamespaces;

        // subscribe to namespace-blocking commands
        pubSubMediator.tell(DistPubSubAccess.subscribe(BlockNamespace.TYPE, getSelf()), getSelf());
        pubSubMediator.tell(DistPubSubAccess.subscribe(UnblockNamespace.TYPE, getSelf()), getSelf());
    }

    /**
     * Creates {@code Props} for this Actor.
     *
     * @param blockedNamespaces the cache for blocked namespaces.
     * @return the Props.
     */
    public static Props props(final BlockedNamespaces blockedNamespaces, final ActorRef pubSubMediator) {

        return Props.create(BlockedNamespacesUpdater.class, blockedNamespaces, pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(BlockNamespace.class, this::blockNamespace)
                .match(UnblockNamespace.class, this::unblockNamespace)
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.info("Got SubscribeAck <{}>", subscribeAck))
                .matchAny(message -> {
                    log.warning("Unhandled message <{}>", message);
                    unhandled(message);
                })
                .build();
    }

    private void blockNamespace(final BlockNamespace command) {
        final String namespace = command.getNamespace();
        final ActorRef sender = getSender();
        final ActorRef self = getSelf();
        blockedNamespaces.add(namespace)
                .thenAccept(unused -> {
                    final var response = BlockNamespaceResponse.getInstance(namespace, command.getResourceType(),
                            command.getDittoHeaders());
                    sender.tell(response, self);
                })
                .exceptionally(error -> handleError(error, command, sender));
    }

    private void unblockNamespace(final UnblockNamespace command) {
        final String namespace = command.getNamespace();
        final ActorRef sender = getSender();
        final ActorRef self = getSelf();
        blockedNamespaces.remove(namespace)
                .thenAccept(unused -> {
                    final var response = UnblockNamespaceResponse.getInstance(namespace, command.getResourceType(),
                            command.getDittoHeaders());
                    sender.tell(response, self);
                })
                .exceptionally(error -> handleError(error, command, sender));
    }

    private Void handleError(final Throwable error, final Command<?> command, final ActorRef sender) {
        log.error(error, "Failed to perform <{}>", command);
        final var message = DittoRuntimeException.asDittoRuntimeException(error, t ->
                DittoInternalErrorException.newBuilder()
                        .message(error.getClass() + ": " + error.getMessage())
                        .dittoHeaders(command.getDittoHeaders())
                        .cause(t)
                        .build()
        );
        sender.tell(message, getSelf());
        return null;
    }

}
