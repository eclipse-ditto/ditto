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

import org.eclipse.ditto.services.utils.akka.LogUtil;
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

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "blockedNamespacesUpdater";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final BlockedNamespaces blockedNamespaces;

    private BlockedNamespacesUpdater(final BlockedNamespaces blockedNamespaces) {
        this.blockedNamespaces = blockedNamespaces;
    }

    /**
     * Creates {@code Props} for this Actor.
     *
     * @param blockedNamespaces the cache for blocked namespaces.
     * @return the Props.
     */
    public static Props props(final BlockedNamespaces blockedNamespaces) {
        return Props.create(BlockedNamespacesUpdater.class, () -> new BlockedNamespacesUpdater(blockedNamespaces));
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
        blockedNamespaces.add(namespace).thenAccept(_void -> {
            final BlockNamespaceResponse response =
                    BlockNamespaceResponse.getInstance(namespace, command.getResourceType(), command.getDittoHeaders());
            sender.tell(response, self);
        });
    }

    private void unblockNamespace(final UnblockNamespace command) {
        final String namespace = command.getNamespace();
        final ActorRef sender = getSender();
        final ActorRef self = getSelf();
        blockedNamespaces.add(namespace).thenAccept(_void -> {
            final UnblockNamespaceResponse response =
                    UnblockNamespaceResponse.getInstance(namespace, command.getResourceType(),
                            command.getDittoHeaders());
            sender.tell(response, self);
        });
    }

}
