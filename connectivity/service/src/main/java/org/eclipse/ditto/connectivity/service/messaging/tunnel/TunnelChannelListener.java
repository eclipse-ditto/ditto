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

import java.io.IOException;

import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.forward.TcpipClientChannel;

import akka.actor.ActorRef;
import akka.event.LoggingAdapter;

/**
 * A channel listener that reports exceptions to the {@code SshTunnelActor}.
 */
final class TunnelChannelListener implements ChannelListener {

    private static final String TUNNEL_EXCEPTION_MESSAGE = "Opening SSH channel failed with exception";
    private final ActorRef sshTunnelActor;
    private final LoggingAdapter logger;
    private int initialSshChannelWindowSize;

    /**
     * Instantiates a new {@code TunnelChannelListener}.
     *
     * @param sshTunnelActor actor reference of SshTunnelActor to notify about errors
     * @param initialSshChannelWindowSize the initial window size to use for the RemoteWindow of the SSH channel
     * @param logger the logger
     */
    TunnelChannelListener(final ActorRef sshTunnelActor, final String initialSshChannelWindowSize,
            final LoggingAdapter logger) {
        this.sshTunnelActor = sshTunnelActor;
        this.logger = logger;
        try {
            this.initialSshChannelWindowSize = Integer.parseInt(initialSshChannelWindowSize);
        }
        catch (final NumberFormatException e) {
            final SshTunnelActor.TunnelClosed tunnelClosed =
                    new SshTunnelActor.TunnelClosed(TUNNEL_EXCEPTION_MESSAGE, e);
            sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
        }
    }

    @Override
    public void channelInitialized(final Channel channel) {
        logger.debug("SSH channel initialized: {}", channel);
    }

    @Override
    public void channelOpenSuccess(final Channel channel) {
        if (initialSshChannelWindowSize > 0) {
            // workaround to handle an initial window size of 0
            // If SSH server sends an initial window size of 0 then this causes problems when sending data over
            // the SSH channel right after creation. Writing results in SocketTimeoutException and
            // SSH_MSG_CHANNEL_WINDOW_ADJUST from server is not handled properly.
            // Expanding the remote window fixes this problem
            channel.getRemoteWindow().expand(initialSshChannelWindowSize);
        }
        logger.debug("SSH channel opened successfully: {}", channel);
    }

    @Override
    public void channelOpenFailure(final Channel channel, final Throwable reason) {
        if (reason != null) {
            final SshTunnelActor.TunnelClosed tunnelClosed =
                    new SshTunnelActor.TunnelClosed(TUNNEL_EXCEPTION_MESSAGE, reason);
            sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
        }
    }

    @Override
    public void channelStateChanged(final Channel channel, final String hint) {
        logger.debug("SSH channel state changed for {}. Reason of change was: {}", channel, hint);
    }

    @Override
    public void channelClosed(final Channel channel, final Throwable reason) {

        // channelClosed with empty reason is a normal closing
        if (reason != null) {
            final SshTunnelActor.TunnelClosed tunnelClosed =
                    new SshTunnelActor.TunnelClosed("SSH channel closed with exception", reason);
            sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
        }

        // attach a listener to the open future, otherwise we have no access to the exception that caused the opening
        // to fail (e.g. channelOpenFailure is not called with an exception)
        if (channel instanceof TcpipClientChannel tcpipClientChannel) {
            try {
                final OpenFuture openFuture = tcpipClientChannel.open();
                if (openFuture != null) {
                    tcpipClientChannel.open()
                            .addListener(future -> {
                                final Throwable exception = future.getException();
                                if (exception != null) {
                                    tellExceptionToTunnelActor(exception);
                                }
                            });
                }
            } catch (IOException e) {
                tellExceptionToTunnelActor(e);
            }
        }
    }

    private void tellExceptionToTunnelActor(final Throwable exception) {
        final SshTunnelActor.TunnelClosed tunnelClosed =
                new SshTunnelActor.TunnelClosed(TUNNEL_EXCEPTION_MESSAGE, exception);
        sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
    }

}
