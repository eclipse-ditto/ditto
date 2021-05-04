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

/**
 * A channel listener that reports exceptions to the {@code SshTunnelActor}.
 */
final class TunnelChannelListener implements ChannelListener {

    private final ActorRef sshTunnelActor;

    /**
     * Instantiates a new {@code TunnelChannelListener}.
     *
     * @param sshTunnelActor actor reference of SshTunnelActor to notify about errors
     */
    TunnelChannelListener(final ActorRef sshTunnelActor) {this.sshTunnelActor = sshTunnelActor;}

    @Override
    public void channelOpenFailure(final Channel channel, final Throwable reason) {
        if (reason != null) {
            final SshTunnelActor.TunnelClosed tunnelClosed =
                    new SshTunnelActor.TunnelClosed("Opening channel failed with exception", reason);
            sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
        }
    }

    @Override
    public void channelClosed(final Channel channel, final Throwable reason) {

        // channelClosed with empty reason is a normal closing
        if (reason != null) {
            final SshTunnelActor.TunnelClosed tunnelClosed =
                    new SshTunnelActor.TunnelClosed("Channel closed with exception", reason);
            sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
        }

        // attach a listener to the open future, otherwise we have no access to the exception that caused the opening
        // to fail (e.g. channelOpenFailure is not called with an exception)
        if (channel instanceof TcpipClientChannel) {
            final TcpipClientChannel tcpipClientChannel = (TcpipClientChannel) channel;
            final OpenFuture openFuture = tcpipClientChannel.getOpenFuture();
            if (openFuture != null) {
                tcpipClientChannel.getOpenFuture()
                        .addListener(future -> {
                            final Throwable exception = future.getException();
                            if (exception != null) {
                                final SshTunnelActor.TunnelClosed tunnelClosed =
                                        new SshTunnelActor.TunnelClosed("Opening channel failed with exception",
                                                exception);
                                sshTunnelActor.tell(tunnelClosed, ActorRef.noSender());
                            }
                        });
            }
        }
    }
}
