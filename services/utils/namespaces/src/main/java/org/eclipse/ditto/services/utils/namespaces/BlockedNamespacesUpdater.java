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
package org.eclipse.ditto.services.utils.namespaces;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.namespaces.BlockNamespace;
import org.eclipse.ditto.signals.commands.namespaces.BlockNamespaceResponse;
import org.eclipse.ditto.signals.commands.namespaces.UnblockNamespace;
import org.eclipse.ditto.signals.commands.namespaces.UnblockNamespaceResponse;

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

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final BlockedNamespaces blockedNamespaces;

    @SuppressWarnings("unused")
    private BlockedNamespacesUpdater(final BlockedNamespaces blockedNamespaces, final ActorRef pubSubMediator) {
        this.blockedNamespaces = blockedNamespaces;

        // subscribe to namespace-blocking commands
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(BlockNamespace.TYPE, getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(UnblockNamespace.TYPE, getSelf()), getSelf());
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
                .thenAccept(_void -> {
                    final BlockNamespaceResponse response =
                            BlockNamespaceResponse.getInstance(namespace, command.getResourceType(),
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
                .thenAccept(_void -> {
                    final UnblockNamespaceResponse response =
                            UnblockNamespaceResponse.getInstance(namespace, command.getResourceType(),
                                    command.getDittoHeaders());
                    sender.tell(response, self);
                })
                .exceptionally(error -> handleError(error, command, sender));
    }

    private Void handleError(final Throwable error, final Command<?> command, final ActorRef sender) {
        log.error(error, "Failed to perform <{}>", command);
        final DittoRuntimeException message;
        if (error instanceof DittoRuntimeException) {
            message = (DittoRuntimeException) error;
        } else {
            message = GatewayInternalErrorException.newBuilder()
                    .message(error.getClass() + ": " + error.getMessage())
                    .build();
        }
        sender.tell(message, getSelf());
        return null;
    }

}
