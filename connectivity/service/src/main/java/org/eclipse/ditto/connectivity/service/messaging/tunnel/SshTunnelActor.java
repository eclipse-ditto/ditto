/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.tunnel;

import static org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelActor.TunnelControl.START_TUNNEL;
import static org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelActor.TunnelControl.STOP_TUNNEL;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.auth.UserAuthMethodFactory;
import org.apache.sshd.common.future.SshFuture;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.SshTunnel;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;

/**
 * Establishes an SSH tunnel using to the data from the given connection. The tunnel can be started/stopped with the
 * respective {@code TunnelControl} messages. The random local port of the ssh tunnel is sent to the parent actor (client actor)
 * with a {@code TunnelStarted} message. Any error that occurs is propagated to the parent actor via a {@code
 * TunnelClosed} message.
 */
public final class SshTunnelActor extends AbstractActorWithTimers implements CredentialsVisitor<Void> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "sshTunnel";

    private final LoggingAdapter logger;
    private final Connection connection;
    private final ConnectivityStatusResolver connectivityStatusResolver;
    private final ConnectionLogger connectionLogger;
    private final SshClient sshClient;
    private final String sshHost;
    private final int sshPort;
    private final ServerKeyVerifier serverKeyVerifier;
    private Instant inStateSince = Instant.EPOCH;

    @Nullable private String sshUser = null;
    @Nullable private String sshUserAuthMethod = null;

    @Nullable private ClientSession sshSession = null;

    // holds the first error that occurred
    @Nullable Throwable error = null;

    @SuppressWarnings("unused")
    private SshTunnelActor(final Connection connection,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectionLogger connectionLogger) {
        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());
        this.connection = connection;
        this.connectivityStatusResolver = connectivityStatusResolver;
        this.connectionLogger = connectionLogger;
        sshClient = SshClientProvider.get(getContext().getSystem()).getSshClient();

        final SshTunnel sshTunnel = this.connection.getSshTunnel()
                .orElseThrow(() -> ConnectionFailedException
                        .newBuilder(connection.getId())
                        .message("Tunnel actor started without tunnel configuration.")
                        .build());
        final URI sshUri = URI.create(sshTunnel.getUri());
        sshHost = sshUri.getHost();
        sshPort = sshUri.getPort();

        sshTunnel.getCredentials().accept(this);

        if (sshTunnel.isValidateHost()) {
            serverKeyVerifier = new FingerprintVerifier(sshTunnel.getKnownHosts());
        } else {
            serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE;
        }
    }

    /**
     * Create Akka actor configuration Props object for the SSH tunnel actor.
     *
     * @param connection the connection to create the SSH tunnel for.
     * @param connectivityStatusResolver connectivity status resolver to resolve the SSH tunnel status based on
     * exceptions.
     * @param connectionLogger the connection logger to use for logging.
     * @return the Props object.
     */
    public static Props props(final Connection connection,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectionLogger connectionLogger) {
        return Props.create(SshTunnelActor.class, connection, connectivityStatusResolver, connectionLogger);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(START_TUNNEL, startTunnel -> handleStartTunnel())
                .match(ConnectFuture.class, this::handleConnectResult)
                .match(AuthFuture.class, this::handleAuthResult)
                .match(TunnelClosed.class, this::handleTunnelClosed)
                .matchEquals(STOP_TUNNEL, stopTunnel -> cleanupActorState(null))
                .match(RetrieveAddressStatus.class, ras -> getSender().tell(getResourceStatus(), getSelf()))
                .matchAny(m -> {
                    logger.debug("Cannot handle {}", m.getClass().getName());
                    unhandled(m);
                })
                .build();
    }

    private void handleStartTunnel() {
        if (sshSession == null || (sshSession.isClosed() || sshSession.isClosing())) {
            cleanupActorState(null);
            try {
                logger.debug("Connecting to ssh server at {}:{}", sshHost, sshPort);
                final ConnectFuture connectFuture;
                connectFuture = sshClient.connect(sshUser, sshHost, sshPort);
                pipeToSelf(connectFuture);
            } catch (final IOException ioException) {
                notifyParentAndCleanup("Failed connecting to SSH server.", ioException);
            }
        } else if (sshSession.isOpen() && sshSession.getStartedLocalPortForwards().size() == 1) {
            final int localPort = sshSession.getStartedLocalPortForwards().get(0).getPort();
            logger.debug("SSH tunnel already established at local port {}.", localPort);
            getContext().getParent().tell(new TunnelStarted(localPort), getSelf());
        } else {
            final int forwards = sshSession.getStartedLocalPortForwards().size();
            final String status = sshSession.isOpen() ? "open" : "closed";
            final String msg =
                    String.format("Inconsistent tunnel state. Session %s with %d port forwards.", status, forwards);
            notifyParentAndCleanup(msg);
        }
    }

    private void handleConnectResult(final ConnectFuture connectFuture) throws IOException {
        if (connectFuture.isConnected()) {
            logger.debug("SSH session connected successfully.");
            sshSession = connectFuture.getClientSession();
            sshSession.addSessionListener(new TunnelSessionListener(getSelf(), logger));
            sshSession.addChannelListener(new TunnelChannelListener(getSelf()));
            sshSession.setServerKeyVerifier(serverKeyVerifier);
            sshSession.setUserAuthFactoriesNames(sshUserAuthMethod);
            connection.getSshTunnel()
                    .map(SshTunnel::getCredentials)
                    .ifPresent(c -> c.accept(new ClientSessionCredentialsVisitor(sshSession, logger)));
            pipeToSelf(sshSession.auth());
        } else {
            connectionLogger.failure("SSH connection failed: {0}", getMessage(connectFuture.getException()));
            notifyParentAndCleanup("Failed to connect to ssh server", connectFuture.getException());
        }
    }

    private void handleAuthResult(final AuthFuture authFuture) {
        if (authFuture.isSuccess()) {
            logger.debug("SSH session authenticated successfully.");
            try {
                final URI uri = URI.create(connection.getUri());
                final SshdSocketAddress targetAddress = new SshdSocketAddress(uri.getHost(), uri.getPort());
                final SshdSocketAddress localAddress =
                        sshSession.startLocalPortForwarding(0, targetAddress);

                connectionLogger.success("SSH tunnel established successfully");

                inStateSince = Instant.now();
                final TunnelStarted tunnelStarted = new TunnelStarted(localAddress.getPort());
                getContext().getParent().tell(tunnelStarted, getSelf());
            } catch (final Exception ioException) {
                connectionLogger.failure("SSH session authentication failed: {0}", getMessage(ioException));
                notifyParentAndCleanup("Failed to start local port forwarding", ioException);
            }
        } else {
            connectionLogger.failure("SSH session authentication failed: {0}", getMessage(authFuture.getException()));
            notifyParentAndCleanup("Failed to authenticate at SSH server.", authFuture.getException());
        }
    }

    private void handleTunnelClosed(final TunnelClosed tunnelClosed) {
        if (tunnelClosed.getError() != null) {
            connectionLogger.failure("SSH Tunnel failed: {0}", getMessage(tunnelClosed.getError()));
        } else {
            connectionLogger.success("SSH Tunnel closed");
        }
        notifyParentAndCleanup(tunnelClosed);
    }

    @Override
    public void postStop() {
        logger.debug("Actor stopped, closing tunnel.");
        cleanupActorState(null);
    }

    private void notifyParentAndCleanup(final String errorMessage) {
        notifyParentAndCleanup(new TunnelClosed(errorMessage, new IllegalStateException(errorMessage)));
    }

    private void notifyParentAndCleanup(final String errorMessage, final Throwable t) {
        notifyParentAndCleanup(new TunnelClosed(errorMessage, t));
    }

    private void notifyParentAndCleanup(final TunnelClosed tunnelClosed) {
        logException(tunnelClosed.getMessage(), tunnelClosed.getError());
        if (error == null) {
            // propagate only the first error that occurred
            getContext().getParent().tell(tunnelClosed, getSelf());
        }
        cleanupActorState(tunnelClosed.getError());
    }

    private void cleanupActorState(@Nullable final Throwable t) {
        inStateSince = Instant.now();
        if (error == null || t == null) {
            error = t;
        }
        if (sshSession != null) {
            try {
                sshSession.close();
                sshSession = null;
            } catch (final IOException ioException) {
                logger.debug("Closing ssh session failed: {}", getMessage(ioException));
            }
        }
    }

    private void logException(final String context, @Nullable final Throwable exception) {
        final String template = context + ": {}";
        logger.info(template, getMessage(exception));
    }

    private String getMessage(@Nullable final Throwable exception) {
        return exception != null
                ? String.format("[%s] %s", exception.getClass().getName(), exception.getMessage())
                : "<no reason>";
    }

    private <T extends SshFuture<T>> void pipeToSelf(final SshFuture<T> sshFuture) {
        final CompletableFuture<T> f = new CompletableFuture<>();
        Patterns.pipe(f, getContext().getDispatcher()).to(getSelf());
        sshFuture.addListener(f::complete);
    }

    private ResourceStatus getResourceStatus() {
        final ConnectivityStatus status;
        final String statusDetail;
        if (sshSession != null && sshSession.isOpen()) {
            status = ConnectivityStatus.OPEN;
            statusDetail = "ssh tunnel established.";
        } else if (error != null) {
            status = connectivityStatusResolver.resolve(error);
            statusDetail = ConnectionFailure.determineFailureDescription(Instant.now(), error, "SSH tunnel failed");
        } else {
            status = ConnectivityStatus.CLOSED;
            statusDetail = "ssh tunnel closed.";
        }
        return ConnectivityModelFactory.newSshTunnelStatus(InstanceIdentifierSupplier.getInstance()
                .get(), status, statusDetail, inStateSince);
    }

    @Override
    public Void clientCertificate(final ClientCertificateCredentials credentials) {
        // not supported
        return null;
    }

    @Override
    public Void usernamePassword(final UserPasswordCredentials credentials) {
        sshUser = credentials.getUsername();
        sshUserAuthMethod = UserAuthMethodFactory.PASSWORD;
        logger.debug("Username ({}) for ssh session is '{}'.", sshUserAuthMethod, sshUser);
        return null;
    }

    @Override
    public Void sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        sshUser = credentials.getUsername();
        sshUserAuthMethod = UserAuthMethodFactory.PUBLIC_KEY;
        logger.debug("Username ({}) for ssh session is '{}'.", sshUserAuthMethod, sshUser);
        return null;
    }

    @Override
    public Void hmac(final HmacCredentials credentials) {
        // not supported
        return null;
    }

    @Override
    public Void oauthClientCredentials(final OAuthClientCredentials credentials) {
        // not supported
        return null;
    }

    /**
     * TunnelClosed event sent to parent (client actor) to notify about changes to the tunnel state.
     */
    public static final class TunnelStarted {

        private final int localPort;

        private TunnelStarted(final int localPort) {
            this.localPort = localPort;
        }

        /**
         * @return the local port of the ssh tunnel
         */
        public int getLocalPort() {
            return localPort;
        }

    }

    /**
     * TunnelClosed event sent to parent (client actor) to notify about changes to the tunnel state.
     */
    public static final class TunnelClosed {

        private final String message;
        @Nullable private final Throwable reason;

        TunnelClosed(final String message, final Throwable reason) {
            this.message = message;
            this.reason = reason;
        }

        TunnelClosed(final String message) {
            this.message = message;
            this.reason = null;
        }

        /**
         * @return the reason why the tunnel was closed
         */
        public String getMessage() {
            return message;
        }

        /**
         * @return an optional error why the tunnel was closed
         */
        @Nullable
        public Throwable getError() {
            return reason;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "message=" + message +
                    ", reason=" + reason +
                    "]";
        }
    }

    /**
     * Control messages to start/stop tunnels.
     */
    public enum TunnelControl {
        START_TUNNEL,
        STOP_TUNNEL
    }

}
