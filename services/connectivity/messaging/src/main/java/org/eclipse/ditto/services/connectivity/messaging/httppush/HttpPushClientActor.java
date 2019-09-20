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
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

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
        final Uri uri = Uri.create(connection.getUri());
        final String testMethod = connection.getSpecificConfig().get(HttpPushFactory.TEST_METHOD);
        final String testStatus = connection.getSpecificConfig().get(HttpPushFactory.TEST_STATUS);
        final boolean isTestRequestDefined = testMethod != null && testStatus != null;
        if (!isTestRequestDefined && HttpPushValidator.isSecureScheme(Uri.create(connection.getUri()).getScheme())) {
            return testSSL(connection, uri.getHost().address(), uri.port());
        } else if (isTestRequestDefined) {
            // test request tests also SSL connection if the connection is secure.
            return testRequest(connection, testMethod, testStatus,
                    connection.getSpecificConfig().getOrDefault(HttpPushFactory.TEST_PATH, ""));
        } else {
            // non-secure HTTP without test request; succeed after TCP connection.
            return statusSuccessFuture("TCP connection to '%s:%d' established successfully",
                    uri.getHost().address(), uri.getPort());
        }
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

    private CompletionStage<Status.Status> testRequest(final Connection connection, final String method,
            final String status, final String path) {
        final Optional<HttpMethod> httpMethod = HttpMethods.lookup(method);
        if (httpMethod.isPresent()) {
            return Source.single(factory.newRequest(HttpPublishTarget.of(path)).withMethod(httpMethod.get()))
                    .map(r -> Pair.create(r, null))
                    .via(factory.createFlow(getContext().getSystem(), log))
                    .map(Pair::first)
                    .<Status.Status>map(tryResponse -> {
                        if (tryResponse.isFailure()) {
                            return new Status.Failure(tryResponse.failed().get());
                        } else {
                            final HttpResponse response = tryResponse.get();
                            if (String.valueOf(response.status().intValue()).equals(status)) {
                                return new Status.Success(String.format(
                                        "%s-request to '%s' completed successfully with status '%s'.",
                                        method, connection.getUri(), status));
                            } else {
                                final String errorMessage = String.format("%s-request to '%s' completed with a " +
                                                "different status '%d' than the expected status '%s'.",
                                        method, connection.getUri(), response.status().intValue(), status);
                                final ConnectionFailedException error =
                                        ConnectionFailedException.newBuilder(connection.getId())
                                                .message(errorMessage)
                                                .build();
                                return new Status.Failure(error);
                            }
                        }
                    })
                    .runWith(Sink.head(), ActorMaterializer.create(getContext()));
        } else {
            return statusFailureFuture(HttpPushValidator.testMethodNotFound(method, DittoHeaders.empty()));
        }
    }

    private static CompletionStage<Status.Status> statusSuccessFuture(final String template, final Object... args) {
        return CompletableFuture.completedFuture(new Status.Success(String.format(template, args)));
    }

    private static CompletionStage<Status.Status> statusFailureFuture(final Throwable error) {
        return CompletableFuture.completedFuture(new Status.Failure(error));
    }
}
