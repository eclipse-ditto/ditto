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
package org.eclipse.ditto.services.utils.namespaces;

import static akka.cluster.pubsub.DistributedPubSubMediator.Put;

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
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which updates the distributed cache of blocked namespaces.
 */
public final class BlockedNamespacesUpdater extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final BlockedNamespaces blockedNamespaces;

    private BlockedNamespacesUpdater(final BlockedNamespaces blockedNamespaces, final ActorRef pubSubMediator) {
        this.blockedNamespaces = blockedNamespaces;

        // register self for pub-sub on restart
        pubSubMediator.tell(new Put(getSelf()), getSelf());
    }

    /**
     * Creates {@code Props} for this Actor.
     *
     * @param blockedNamespaces the cache for blocked namespaces.
     * @return the Props.
     */
    public static Props props(final BlockedNamespaces blockedNamespaces, final ActorRef pubSubMediator) {
        return Props.create(BlockedNamespacesUpdater.class,
                () -> new BlockedNamespacesUpdater(blockedNamespaces, pubSubMediator));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(BlockNamespace.class, this::blockNamespace)
                .match(UnblockNamespace.class, this::unblockNamespace)
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
