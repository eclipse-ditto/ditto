/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * A TCP gate on a fixed loopback port in front of a PostgreSQL server, for ITs in which PostgreSQL is unreachable
 * first and reachable later — deterministic, with the real driver against a real server, and independent of how Docker
 * publishes ports. (Pausing a container does not make PostgreSQL unreachable: its published port still accepts
 * connections that are then never answered, which is the "stuck" case, not the "unreachable" one.)
 */
public final class TcpGate implements Closeable {

    /** What the gate does with a connection. */
    public enum Mode {
        /** Nothing listens: connection refused (PostgreSQL not started). */
        CLOSED,
        /** Accept, then close with a TCP reset (initdb, or a published port with nothing behind it). */
        RESET,
        /** Forward to the target (PostgreSQL available). */
        OPEN,
        /** Accept and never answer (a stuck endpoint). */
        BLACKHOLE
    }

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final int port;
    private final String targetHost;
    private final int targetPort;
    private final AtomicInteger accepted = new AtomicInteger();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private volatile Mode mode = Mode.CLOSED;
    @Nullable private ServerSocket server;

    private TcpGate(final int port, final String targetHost, final int targetPort) {
        this.port = port;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    /**
     * @param targetHost the host to forward to in {@link Mode#OPEN}.
     * @param targetPort the port to forward to in {@link Mode#OPEN}.
     * @return a gate on a free loopback port, in {@link Mode#CLOSED}.
     */
    public static TcpGate to(final String targetHost, final int targetPort) {
        return new TcpGate(freePort(), targetHost, targetPort);
    }

    /**
     * @return a loopback port nothing listens on right now, so a connection to it is refused.
     */
    public static int freePort() {
        try (ServerSocket probe = new ServerSocket(0, 0, LOOPBACK)) {
            return probe.getLocalPort();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return the literal loopback address, so that no client tries another address family first. */
    public String getHost() {
        return LOOPBACK.getHostAddress();
    }

    /** @return the gate's port. */
    public int getPort() {
        return port;
    }

    /** @return how many connections the gate has accepted (in any mode but {@link Mode#CLOSED}). */
    public int acceptedConnections() {
        return accepted.get();
    }

    /**
     * Switches the mode for new connections; connections already forwarded stay open.
     *
     * @param newMode the new mode.
     */
    public synchronized void setMode(final Mode newMode) {
        mode = newMode;
        if (newMode == Mode.CLOSED) {
            closeServer();
        } else if (server == null) {
            final ServerSocket bound = bind();
            server = bound;
            startDaemon("tcp-gate-accept-" + port, () -> acceptLoop(bound));
        }
    }

    @Override
    public synchronized void close() {
        mode = Mode.CLOSED;
        closeServer();
        sockets.forEach(TcpGate::closeQuietly);
        sockets.clear();
    }

    private ServerSocket bind() {
        try {
            final ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(LOOPBACK, port));
            return socket;
        } catch (final IOException e) {
            throw new UncheckedIOException("TcpGate could not bind port " + port, e);
        }
    }

    private void closeServer() {
        if (server != null) {
            closeQuietly(server);
            server = null;
        }
    }

    private void acceptLoop(final ServerSocket bound) {
        while (!bound.isClosed()) {
            final Socket client;
            try {
                client = bound.accept();
            } catch (final IOException e) {
                return;   // closed by setMode(CLOSED) or close()
            }
            accepted.incrementAndGet();
            sockets.add(client);
            switch (mode) {
                case OPEN -> startDaemon("tcp-gate-forward-" + port, () -> forward(client));
                case BLACKHOLE -> { }   // keep the socket open and never answer
                default -> reset(client);
            }
        }
    }

    private void forward(final Socket client) {
        try {
            final Socket upstream = new Socket(targetHost, targetPort);
            sockets.add(upstream);
            startDaemon("tcp-gate-pump-" + port, () -> pump(client, upstream));
            pump(upstream, client);
        } catch (final IOException e) {
            reset(client);
        }
    }

    private static void pump(final Socket from, final Socket to) {
        try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
            in.transferTo(out);
        } catch (final IOException ignored) {
            // either side closed
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private static void reset(final Socket socket) {
        try {
            socket.setSoLinger(true, 0);
        } catch (final IOException ignored) {
            // already closed
        }
        closeQuietly(socket);
    }

    private static void startDaemon(final String name, final Runnable task) {
        final Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void closeQuietly(final Closeable closeable) {
        try {
            closeable.close();
        } catch (final IOException ignored) {
            // nothing to do
        }
    }
}
