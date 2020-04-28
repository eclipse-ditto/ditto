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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.base.config.http.HttpProxyConfig;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.HttpPushConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.model.Uri;

/**
 * Client actor for HTTP-push.
 */
public final class HttpPushClientActor extends BaseClientActor {

    private static final int PROXY_CONNECT_TIMEOUT_SECONDS = 15;

    private final HttpPushFactory factory;

    @Nullable
    private ActorRef httpPublisherActor;
    private final HttpPushConfig httpPushConfig;

    @SuppressWarnings("unused")
    private HttpPushClientActor(final Connection connection, final ActorRef connectionActor) {
        super(connection, ActorRef.noSender(), connectionActor);

        httpPushConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        )
                .getConnectionConfig()
                .getHttpPushConfig();
        factory = HttpPushFactory.of(connection, httpPushConfig);
    }

    /**
     * Create the {@code Props} object for an {@code HttpPushClientActor}.
     *
     * @param connection the HTTP-push connection.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @return the {@code Props} object.
     */
    public static Props props(final Connection connection, final ActorRef connectionActor) {
        return Props.create(HttpPushClientActor.class, connection, connectionActor);
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
        final Props props = HttpPublisherActor.props(connection(), factory);
        httpPublisherActor = startChildActorConflictFree(HttpPublisherActor.ACTOR_NAME, props);
        future.complete(DONE);
        return future;
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
                    SSLContextCreator.fromConnection(connection, DittoHeaders.empty());
            final SSLSocketFactory socketFactory = sslContextCreator.withoutClientCertificate().getSocketFactory();
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
                byte[] tmpBuffer = new byte[512];
                final InputStream socketInput = proxySocket.getInputStream();
                int len = socketInput.read(tmpBuffer, 0, tmpBuffer.length);
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
                    ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());
                    log.info("Could not connect to <{}> via Http Proxy <{}>", hostWithoutLookup + ":" + port,
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
}
