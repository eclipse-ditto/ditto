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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fools the fast-failing mechanism of BaseClientActor so that MqttClientActor can be tested.
 * BaseClientActor does not attempt to connect if the host address of the connection URI is not reachable.
 */
public final class MqttServerRule extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttServerRule.class);

    private final int port;

    @Nullable
    private ServerSocket serverSocket;

    public MqttServerRule(final int port) {
        LOGGER.info("Starting server at port {}", port);
        this.port = port;
    }

    @Override
    protected void before() throws Exception {
        serverSocket = new ServerSocket(port);
        // start server that closes accepted socket immediately
        // so that failure is not triggered by connection failure shortcut
        CompletableFuture.runAsync(() -> {
            while (true) {
                try (final Socket socket = serverSocket.accept()) {
                    LOGGER.info("Incoming connection to port {} accepted at port {} ",
                            serverSocket.getLocalPort(),
                            socket.getPort());
                } catch (final IOException e) {
                    // server socket closed, quitting.
                    break;
                }
            }
        });
    }

    @Override
    protected void after() {
        try {
            if (serverSocket != null) {
                // should complete future exceptionally via IOException in dispatcher thread
                serverSocket.close();
            }
        } catch (final IOException e) {
            // don't care; next test uses a new port.
        }
    }

}
