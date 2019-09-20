/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;

/**
 * Client actor for HTTP-push.
 */
public final class HttpPushClientActor extends BaseClientActor {

    private final HttpPushFactory factory;

    protected HttpPushClientActor(final Connection connection, final ConnectivityStatus desiredConnectionStatus) {
        super(connection, desiredConnectionStatus, ActorRef.noSender());
        factory = HttpPushFactory.of(connection);
    }

    /**
     * Create the {@code Props} object for an {@code HttpPushClientActor}.
     *
     * @param connection the HTTP-push connection.
     * @return the {@code Props} object.
     */
    public static Props props(final Connection connection) {
        return Props.create(HttpPushClientActor.class, connection, connection.getConnectionStatus());
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        // TODO: test SSL and possibly request/response
        return CompletableFuture.completedFuture(new Status.Success(getSelf()));
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // all 0 consumers are ready.
        notifyConsumersReady();
    }

    @Override
    protected void cleanupResourcesForConnection() {
        // noop
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        ensurePublisherActor();
        getSelf().tell((ClientConnected) () -> Optional.ofNullable(origin), getSelf());
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        stopPublisherActor();
        getSelf().tell((ClientDisconnected) () -> Optional.ofNullable(origin), getSelf());
    }

    private void ensurePublisherActor() {
        if (publisherActor == null) {
            publisherActor =
                    getContext().actorOf(HttpPublisher.props(connection().getId(), connection().getTargets(), factory));
        }
    }
}
