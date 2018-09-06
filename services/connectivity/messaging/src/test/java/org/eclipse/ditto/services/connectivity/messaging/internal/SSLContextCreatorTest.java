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

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.credentials.ClientCertificateCredentials;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.internal.SSLContextCreator}.
 */
public final class SSLContextCreatorTest {

    // signs server and client certs
    private static final String CA_CRT = getCert("ca.crt");

    private static final ClientCertificateCredentials SERVER_CREDENTIALS = ClientCertificateCredentials.newBuilder()
            .clientKey(getCert("server.key"))
            .clientCertificate(getCert("server.crt"))
            .build();

    private static final ClientCertificateCredentials CLIENT_CREDENTIALS = ClientCertificateCredentials.newBuilder()
            .clientKey(getCert("client.key"))
            .clientCertificate(getCert("client.crt"))
            .build();

    private static final ClientCertificateCredentials SELF_SIGNED_CLIENT_CREDENTIALS =
            ClientCertificateCredentials.newBuilder()
                    .clientKey(getCert("client-self-signed.key"))
                    .clientCertificate(getCert("client-self-signed.crt"))
                    .build();

    @Test
    public void doesNotTrustSelfSignedServer() throws Exception {
        try (final ServerSocket serverSocket = startServer(false)) {
            assertThatExceptionOfType(SSLHandshakeException.class).isThrownBy(() -> {
                try (final Socket underTest = SSLContextCreator.of(null, DittoHeaders.empty())
                        .clientCertificate(ClientCertificateCredentials.empty())
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                    underTest.getOutputStream().write(1234);
                }
            });
        }
    }

    @Test
    public void trustSignedServer() throws Exception {
        try (final ServerSocket serverSocket = startServer(false);
                final Socket underTest = SSLContextCreator.of(CA_CRT, DittoHeaders.empty())
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
                    final Socket underTest = SSLContextCreator.of(CA_CRT, DittoHeaders.empty())
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
                final Socket underTest = SSLContextCreator.of(CA_CRT, DittoHeaders.empty())
                        .clientCertificate(CLIENT_CREDENTIALS)
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(45);
            assertThat(underTest.getInputStream().read()).isEqualTo(45);
        }
    }

    private ServerSocket startServer(final boolean needClientAuth) throws Exception {
        final SSLServerSocket serverSocket =
                (SSLServerSocket) SSLContextCreator.of(CA_CRT, null)
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

    private static String getCert(final String cert) {
        final String path = "/certificates/" + cert;
        try (final InputStream inputStream = SSLContextCreatorTest.class.getResourceAsStream(path)) {
            final Scanner scanner = new Scanner(inputStream, StandardCharsets.US_ASCII.name()).useDelimiter("\\A");
            return scanner.next();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
