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
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.model.Uri;

/**
 * Client actor for HTTP-push.
 */
public final class HttpPushClientActor extends BaseClientActor {

    private final HttpPushFactory factory;

    @Nullable
    private ActorRef httpPublisherActor;

    @SuppressWarnings("unused")
    private HttpPushClientActor(final Connection connection, final ConnectivityStatus desiredConnectionStatus) {
        super(connection, desiredConnectionStatus, ActorRef.noSender());
        factory = HttpPushFactory.of(connection, null);
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
        final Uri uri = Uri.create(connection.getUri());
        if (HttpPushValidator.isSecureScheme(uri.getScheme())) {
            return testSSL(connection, uri.getHost().address(), uri.port());
        } else {
            // non-secure HTTP without test request; succeed after TCP connection.
            return statusSuccessFuture("TCP connection to '%s:%d' established successfully",
                    uri.getHost().address(), uri.getPort());
        }
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here; publisher and consumers (no consumers for HTTP) started already.
    }

    @Override
    protected void cleanupResourcesForConnection() {
        // stop publisher actor also on connection failure
        stopChildActor(httpPublisherActor);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        getSelf().tell((ClientConnected) () -> Optional.ofNullable(origin), getSelf());
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        getSelf().tell((ClientDisconnected) () -> Optional.ofNullable(origin), getSelf());
    }

    @Nullable
    @Override
    protected ActorRef getPublisherActor() {
        return httpPublisherActor;
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        final CompletableFuture<Status.Status> future = new CompletableFuture<>();
        stopChildActor(httpPublisherActor);
        final Props props = HttpPublisherActor.props(connection().getId(), connection().getTargets(), factory);
        httpPublisherActor = startChildActorConflictFree(HttpPublisherActor.ACTOR_NAME, props);
        future.complete(DONE);
        return future;
    }

    private CompletionStage<Status.Status> testSSL(final Connection connection, final String hostWithoutLookup,
            final int port) {
        final SSLContextCreator sslContextCreator = SSLContextCreator.fromConnection(connection, DittoHeaders.empty());
        final SSLSocketFactory socketFactory = sslContextCreator.withoutClientCertificate().getSocketFactory();
        try (final SSLSocket socket = (SSLSocket) socketFactory.createSocket(hostWithoutLookup, port)) {
            socket.startHandshake();
            return statusSuccessFuture("TLS connection to '%s:%d' established successfully.", hostWithoutLookup,
                    socket.getPort());
        } catch (final Exception error) {
            return statusFailureFuture(error);
        }
    }

    private static CompletionStage<Status.Status> statusSuccessFuture(final String template, final Object... args) {
        return CompletableFuture.completedFuture(new Status.Success(String.format(template, args)));
    }

    private static CompletionStage<Status.Status> statusFailureFuture(final Throwable error) {
        return CompletableFuture.completedFuture(new Status.Failure(error));
    }
}
