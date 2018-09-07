/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.credentials.ClientCertificateCredentials;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.AcceptAnyTrustManager;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.internal.SSLContextCreator}.
 */
public final class SSLContextCreatorTest {

    private static final ClientCertificateCredentials SERVER_CREDENTIALS = ClientCertificateCredentials.newBuilder()
            .clientKey(Certificates.SERVER_KEY)
            .clientCertificate(Certificates.SERVER_CRT)
            .build();

    private static final ClientCertificateCredentials CLIENT_CREDENTIALS = ClientCertificateCredentials.newBuilder()
            .clientKey(Certificates.CLIENT_KEY)
            .clientCertificate(Certificates.CLIENT_CRT)
            .build();

    private static final ClientCertificateCredentials SELF_SIGNED_CLIENT_CREDENTIALS =
            ClientCertificateCredentials.newBuilder()
                    .clientKey(Certificates.CLIENT_SELF_SIGNED_KEY)
                    .clientCertificate(Certificates.CLIENT_SELF_SIGNED_CRT)
                    .build();

    @Test
    public void doesNotTrustSelfSignedServer() throws Exception {
        try (final ServerSocket serverSocket = startServer(false)) {
            assertThatExceptionOfType(SSLHandshakeException.class).isThrownBy(() -> {
                try (final Socket underTest = SSLContextCreator.of(null, DittoHeaders.empty())
                        .clientCertificate(ClientCertificateCredentials.empty())
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                    underTest.getOutputStream().write(12);
                }
            });
        }
    }

    @Test
    public void trustSignedServer() throws Exception {
        try (final ServerSocket serverSocket = startServer(false);
                final Socket underTest = SSLContextCreator.of(Certificates.CA_CRT, DittoHeaders.empty())
                        .clientCertificate(ClientCertificateCredentials.empty())
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(123);
            assertThat(underTest.getInputStream().read()).isEqualTo(123);
        }
    }

    @Test
    public void doesNotTrustSelfSignedClient() throws Exception {
        assertThatExceptionOfType(SSLHandshakeException.class).isThrownBy(() -> {
            try (final ServerSocket serverSocket = startServer(true);
                    final Socket underTest = SSLContextCreator.of(Certificates.CA_CRT, DittoHeaders.empty())
                            .clientCertificate(SELF_SIGNED_CLIENT_CREDENTIALS)
                            .getSocketFactory()
                            .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                underTest.getOutputStream().write(234);
            }
        });
    }

    @Test
    public void trustSignedClient() throws Exception {
        try (final ServerSocket serverSocket = startServer(true);
                final Socket underTest = SSLContextCreator.of(Certificates.CA_CRT, DittoHeaders.empty())
                        .clientCertificate(CLIENT_CREDENTIALS)
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(45);
            assertThat(underTest.getInputStream().read()).isEqualTo(45);
        }
    }

    @Test
    public void trustEverybodyWithAcceptAnyTrustManager() throws Exception {
        try (final ServerSocket serverSocket = startServer(false);
                final Socket underTest = SSLContextCreator.withTrustManager(new AcceptAnyTrustManager(), null)
                        .clientCertificate(ClientCertificateCredentials.empty())
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(67);
            assertThat(underTest.getInputStream().read()).isEqualTo(67);
        }
    }

    private ServerSocket startServer(final boolean needClientAuth) throws Exception {
        final SSLServerSocket serverSocket =
                (SSLServerSocket) SSLContextCreator.of(Certificates.CA_CRT, null)
                        .clientCertificate(SERVER_CREDENTIALS)
                        .getServerSocketFactory()
                        .createServerSocket(0);

        serverSocket.setNeedClientAuth(needClientAuth);

        CompletableFuture.runAsync(() -> {
            while (true) {
                try (final Socket socket = serverSocket.accept()) {
                    socket.getOutputStream().write(socket.getInputStream().read());
                } catch (final IOException e) {
                    break;
                }
            }
        });

        return serverSocket;
    }

}
