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

import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;

import akka.actor.ActorRef;
import akka.event.LoggingAdapter;

/**
 * A {@link org.apache.sshd.common.session.SessionListener} implementation that reports exceptions to the {@code
 * SshTunnelActor}* and logs session events.
 */
final class TunnelSessionListener implements SessionListener {

    private final ActorRef sshTunnelActor;
    private final LoggingAdapter logger;

    /**
     * Instantiates a new {@code TunnelSessionListener}.
     *
     * @param sshTunnelActor actor reference of SshTunnelActor to notify about errors
     * @param logger the logger
     */
    TunnelSessionListener(final ActorRef sshTunnelActor, final LoggingAdapter logger) {
        this.sshTunnelActor = sshTunnelActor;
        this.logger = logger;
    }

    @Override
    public void sessionException(final Session session, final Throwable reason) {
        logger.debug("Exception occurred in SSH session ({}): {}", session,
                reason != null
                        ? String.format("[%s] %s", reason.getClass().getName(), reason.getMessage())
                        : "<no reason>");
    }

    @Override
    public void sessionClosed(final Session session) {
        logger.debug("SSH session closed: {} ", session);
        sshTunnelActor.tell(new SshTunnelActor.TunnelClosed("Session closed by remote"), ActorRef.noSender());
    }

    @Override
    public void sessionEstablished(final Session session) {
        logger.debug("SSH session established: {}", session);
    }

    @Override
    public void sessionCreated(final Session session) {
        logger.debug("SSH session created: {}", session);
    }

    @Override
    public void sessionDisconnect(final Session session, final int reason, final String msg, final String language,
            final boolean initiator) {
        logger.debug("SSH session disconnected: {}, reason: {}, msg: {}, initiator: {}",
                session, reason, msg, initiator ? "local" : "remote");
    }

}
