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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link TcpGate} against a local echo server: each mode does to a connection what it promises.
 */
public final class TcpGateTest {

    private static final int READ_TIMEOUT_MILLIS = 5_000;

    private ServerSocket echoServer;
    private TcpGate gate;

    @Before
    public void startEchoServerAndGate() throws IOException {
        echoServer = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        final Thread acceptor = new Thread(this::echoLoop, "tcp-gate-test-echo");
        acceptor.setDaemon(true);
        acceptor.start();
        gate = TcpGate.to(echoServer.getInetAddress().getHostAddress(), echoServer.getLocalPort());
    }

    @After
    public void stopEchoServerAndGate() throws IOException {
        gate.close();
        echoServer.close();
    }

    @Test
    public void closedRefusesTheConnection() {
        assertThatThrownBy(() -> new Socket(gate.getHost(), gate.getPort()).close())
                .isInstanceOf(ConnectException.class);
        assertThat(gate.acceptedConnections()).isZero();
    }

    @Test
    public void resetAcceptsAndThenResetsTheConnection() throws IOException {
        gate.setMode(TcpGate.Mode.RESET);
        try (Socket socket = new Socket(gate.getHost(), gate.getPort())) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            try {
                assertThat(socket.getInputStream().read()).isEqualTo(-1);
            } catch (final SocketException reset) {
                // a TCP reset surfaces as "Connection reset" on some platforms
            }
        }
        assertThat(gate.acceptedConnections()).isEqualTo(1);
    }

    @Test
    public void openForwardsToTheTarget() throws IOException {
        gate.setMode(TcpGate.Mode.OPEN);
        try (Socket socket = new Socket(gate.getHost(), gate.getPort())) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            final OutputStream out = socket.getOutputStream();
            out.write("ping".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            final byte[] echoed = socket.getInputStream().readNBytes(4);
            assertThat(new String(echoed, StandardCharsets.US_ASCII)).isEqualTo("ping");
        }
        assertThat(gate.acceptedConnections()).isEqualTo(1);
    }

    @Test
    public void blackholeAcceptsAndNeverAnswers() throws IOException {
        gate.setMode(TcpGate.Mode.BLACKHOLE);
        try (Socket socket = new Socket(gate.getHost(), gate.getPort())) {
            socket.setSoTimeout(200);
            socket.getOutputStream().write(1);
            assertThatThrownBy(() -> socket.getInputStream().read()).isInstanceOf(SocketTimeoutException.class);
        }
        assertThat(gate.acceptedConnections()).isEqualTo(1);
    }

    @Test
    public void closingTheGateAgainRefusesNewConnections() throws IOException {
        gate.setMode(TcpGate.Mode.OPEN);
        new Socket(gate.getHost(), gate.getPort()).close();
        gate.setMode(TcpGate.Mode.CLOSED);
        assertThatThrownBy(() -> new Socket(gate.getHost(), gate.getPort()).close())
                .isInstanceOf(ConnectException.class);
    }

    private void echoLoop() {
        while (!echoServer.isClosed()) {
            try {
                final Socket client = echoServer.accept();
                final Thread echo = new Thread(() -> echo(client), "tcp-gate-test-echo-connection");
                echo.setDaemon(true);
                echo.start();
            } catch (final IOException e) {
                return;
            }
        }
    }

    private static void echo(final Socket client) {
        try (client; InputStream in = client.getInputStream(); OutputStream out = client.getOutputStream()) {
            in.transferTo(out);
        } catch (final IOException ignored) {
            // the test closed the connection
        }
    }

}
