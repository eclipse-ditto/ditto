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

    private final ActorRef sshTunnelActor;
    private final LoggingAdapter logger;

    /**
     * Instantiates a new {@code TunnelChannelListener}.
     *
     * @param sshTunnelActor actor reference of SshTunnelActor to notify about errors
     * @param logger the logger
     */
    TunnelChannelListener(final ActorRef sshTunnelActor, final LoggingAdapter logger) {
        this.sshTunnelActor = sshTunnelActor;
        this.logger = logger;
    }

    @Override
    public void channelInitialized(final Channel channel) {
        logger.debug("SSH channel initialized: {}", channel);
    }

    @Override
    public void channelOpenSuccess(final Channel channel) {
        logger.debug("SSH channel opened successfully: {}", channel);
    }

    @Override
    public void channelOpenFailure(final Channel channel, final Throwable reason) {
        if (reason != null) {
            final SshTunnelActor.TunnelClosed tunnelClosed =
                    new SshTunnelActor.TunnelClosed("Opening SSH channel failed with exception", reason);
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
            final OpenFuture openFuture = tcpipClientChannel.getOpenFuture();
            if (openFuture != null) {
                tcpipClientChannel.getOpenFuture()
                        .addListener(future -> {
                            final Throwable exception = future.getException();
                            if (exception != null) {
                                final SshTunnelActor.TunnelClosed tunnelClosed =
                                        new SshTunnelActor.TunnelClosed("Opening SSH channel failed with exception",
                                                exception);
                                sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
                            }
                        });
            }
        }
    }
}
