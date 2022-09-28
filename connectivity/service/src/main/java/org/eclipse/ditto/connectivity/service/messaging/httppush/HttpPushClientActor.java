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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.eclipse.ditto.connectivity.service.config.MonitoringLoggerConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.util.Try;

/**
 * Client actor for HTTP-push.
 */
public final class HttpPushClientActor extends BaseClientActor {

    private static final int PROXY_CONNECT_TIMEOUT_SECONDS = 15;

    private final HttpPushFactory factory;
    private final HttpPushConfig httpPushConfig;

    @Nullable private ActorRef httpPublisherActor;

    @SuppressWarnings("unused")
    private HttpPushClientActor(final Connection connection,
            final ActorRef connectionActor,
            final ActorRef commandForwarderActor,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        super(connection, commandForwarderActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        httpPushConfig = connectivityConfig().getConnectionConfig().getHttpPushConfig();
        final MonitoringLoggerConfig loggerConfig = connectivityConfig().getMonitoringConfig().logger();
        factory = HttpPushFactory.of(connection, httpPushConfig, connectionLogger, this::getSshTunnelState);
    }

    /**
     * Create the {@code Props} object for an {@code HttpPushClientActor}.
     *
     * @param connection the HTTP-push connection.
     * @param commandForwarderActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param dittoHeaders headers of the command that caused this actor to be created.
     * @param connectivityConfigOverwrites the overwrites for the connectivity config for the given connection.
     * @return the {@code Props} object.
     */
    public static Props props(final Connection connection, final ActorRef commandForwarderActor,
            final ActorRef connectionActor, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        return Props.create(HttpPushClientActor.class, connection, connectionActor, commandForwarderActor, dittoHeaders,
                connectivityConfigOverwrites);
    }

    @Override
    protected boolean canConnectViaSocket(final Connection connection) {
        if (httpPushConfig.getHttpProxyConfig().isEnabled()) {
            return connectViaProxy(connection.getHostname(), connection.getPort())
                    .handle((status, throwable) -> status instanceof Status.Success)
                    .toCompletableFuture()
                    .join();
        } else {
            return super.canConnectViaSocket(connection);
        }
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCommand) {
        final Connection connectionToBeTested = testConnectionCommand.getConnection();
        final Uri uri = Uri.create(connectionToBeTested.getUri());
        final var credentialsTest = testCredentials(connectionToBeTested);
        if (HttpPushValidator.isSecureScheme(uri.getScheme())) {
            return credentialsTest.thenCompose(unused ->
                    testSSL(connectionToBeTested, uri.getHost().address(), uri.port()));
        } else {
            // non-secure HTTP without test request; succeed after TCP connection.
            return credentialsTest.thenCompose(unused ->
                    statusSuccessFuture("TCP connection to '%s:%d' established successfully",
                            uri.getHost().address(), uri.getPort())
            );
        }
    }

    @Override
    protected CompletionStage<Void> stopConsuming() {
        // nothing to do: HTTP connections do not consume.
        return CompletableFuture.completedStage(null);
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here; publisher and consumers (no consumers for HTTP) started already.
    }

    @Override
    protected void cleanupResourcesForConnection() {
        // stop publisher actor also on connection failure
        stopPublisherActor();
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        getSelf().tell((ClientConnected) () -> Optional.ofNullable(origin), getSelf());
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin,
            final boolean shutdownAfterDisconnect) {
        getSelf().tell(ClientDisconnected.of(origin, shutdownAfterDisconnect), getSelf());
    }

    @Nullable
    @Override
    protected ActorRef getPublisherActor() {
        return httpPublisherActor;
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        final CompletableFuture<Status.Status> future = new CompletableFuture<>();
        stopPublisherActor();
        final Props props = HttpPublisherActor.props(connection(),
                factory,
                connectivityStatusResolver,
                connectivityConfig());
        httpPublisherActor = startChildActorConflictFree(HttpPublisherActor.ACTOR_NAME, props);
        future.complete(DONE);

        return future;
    }

    private void stopPublisherActor() {
        if (httpPublisherActor != null) {
            logger.debug("Stopping child actor <{}>.", httpPublisherActor.path());
            // shutdown using a message, so the actor can clean up first
            httpPublisherActor.tell(HttpPublisherActor.GracefulStop.INSTANCE, getSelf());
        }
    }

    private CompletionStage<?> testCredentials(final Connection connection) {
        final var actorSystem = getContext().getSystem();
        final var config = connectivityConfig().getConnectionConfig().getHttpPushConfig();
        return Source.single(Pair.<HttpRequest, HttpPushContext>create(HttpRequest.create(), new TestHttpPushContext()))
                .concat(Source.never())
                .via(ClientCredentialsFlowVisitor.eval(actorSystem, config, connection))
                .runWith(Sink.head(), actorSystem);
    }

    private CompletionStage<Status.Status> testSSL(final Connection connection, final String hostWithoutLookup,
            final int port) {
        if (httpPushConfig.getHttpProxyConfig().isEnabled()) {
            // don't do a second proxy check
            return statusSuccessFuture("TLS connection to '%s:%d' via Http proxy established successfully.",
                    hostWithoutLookup, port);
        } else {
            // check without HTTP proxy
            final SSLContextCreator sslContextCreator =
                    SSLContextCreator.fromConnection(connection, DittoHeaders.empty(), connectionLogger);
            final SSLSocketFactory socketFactory = connection.getCredentials()
                    .map(credentials -> credentials.accept(sslContextCreator))
                    .orElse(sslContextCreator.withoutClientCertificate()).getSocketFactory();
            try (final SSLSocket socket = (SSLSocket) socketFactory.createSocket(hostWithoutLookup, port)) {
                socket.startHandshake();
                return statusSuccessFuture("TLS connection to '%s:%d' established successfully.",
                        hostWithoutLookup, socket.getPort());
            } catch (final Exception error) {
                return statusFailureFuture(error);
            }
        }
    }

    private CompletionStage<Status.Status> connectViaProxy(final String hostWithoutLookup, final int port) {
        final HttpProxyConfig httpProxyConfig = this.httpPushConfig.getHttpProxyConfig();
        try (final Socket proxySocket = new Socket(httpProxyConfig.getHostname(), httpProxyConfig.getPort())) {
            String proxyConnect = "CONNECT " + hostWithoutLookup + ":" + port + " HTTP/1.1\n";
            proxyConnect += "Host: " + hostWithoutLookup + ":" + port;

            if (!httpProxyConfig.getUsername().isEmpty()) {
                final String proxyUserPass = httpProxyConfig.getUsername() + ":" + httpProxyConfig.getPassword();
                proxyConnect += "\nProxy-Authorization: Basic " +
                        Base64.getEncoder().encodeToString(proxyUserPass.getBytes());
            }
            proxyConnect += "\n\n";
            proxySocket.getOutputStream().write(proxyConnect.getBytes());

            return checkProxyConnection(hostWithoutLookup, port, proxySocket);
        } catch (final Exception error) {
            return statusFailureFuture(new SocketException("Failed to connect to HTTP proxy: " + error.getMessage()));
        }
    }

    private CompletionStage<Status.Status> checkProxyConnection(final String hostWithoutLookup, final int port,
            final Socket proxySocket) throws InterruptedException, java.util.concurrent.ExecutionException {

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(() -> {
                final byte[] tmpBuffer = new byte[512];
                final InputStream socketInput = proxySocket.getInputStream();
                final int len = socketInput.read(tmpBuffer, 0, tmpBuffer.length);
                if (len == 0) {
                    socketInput.close();
                    return statusFailureFuture(new SocketException("Invalid response from proxy"));
                }

                final String proxyResponse = new String(tmpBuffer, 0, len, StandardCharsets.UTF_8);
                if (proxyResponse.startsWith("HTTP/1.1 200")) {
                    socketInput.close();
                    return statusSuccessFuture("Connection to '%s:%d' via HTTP proxy established successfully.",
                            hostWithoutLookup, port);
                } else {
                    logger.info("Could not connect to <{}> via Http Proxy <{}>", hostWithoutLookup + ":" + port,
                            proxySocket.getInetAddress());
                    socketInput.close();
                    return statusFailureFuture(new SocketException("Failed to create Socket via HTTP proxy: " +
                            proxyResponse));
                }
            }).get(PROXY_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final TimeoutException timedOut) {
            return statusFailureFuture(
                    new SocketException("Failed to create Socket via HTTP proxy within timeout"));
        } finally {
            executor.shutdown();
        }
    }

    private static CompletionStage<Status.Status> statusSuccessFuture(final String template, final Object... args) {
        return CompletableFuture.completedFuture(new Status.Success(String.format(template, args)));
    }

    private static CompletionStage<Status.Status> statusFailureFuture(final Throwable error) {
        return CompletableFuture.completedFuture(new Status.Failure(error));
    }

    private static class TestHttpPushContext implements HttpPushContext {

        @Override
        public ConnectionMonitor.InfoProvider getInfoProvider() {
            return InfoProviderFactory.empty();
        }

        @Override
        public void onResponse(final Try<HttpResponse> response) {
            // no-op
        }
    }

}
