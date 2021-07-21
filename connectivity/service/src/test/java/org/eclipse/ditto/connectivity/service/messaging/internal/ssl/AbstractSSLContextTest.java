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
package org.eclipse.ditto.connectivity.service.messaging.internal.ssl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Certificates;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;

import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Credentials;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.junit.Test;

/**
 * Tests {@link SSLContextCreator}.
 * <p>
 * Certificates used by this test expires on 01 January 2100. Please regenerate certificates
 * according to {@link org.eclipse.ditto.connectivity.service.messaging.TestConstants.Certificates}.
 * </p>
 */
public abstract class AbstractSSLContextTest {

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

    private static final ClientCertificateCredentials SERVER_WITH_ALT_NAMES =
            ClientCertificateCredentials.newBuilder()
                    .clientKey(Certificates.SERVER_WITH_ALT_NAMES_KEY)
                    .clientCertificate(Certificates.SERVER_WITH_ALT_NAMES_CRT)
                    .build();

    private static final String HOSTNAME = "example.com";

    abstract SSLContext createSSLContext(final String trustedCertificates, final String hostname,
            final Credentials credentials) throws Exception;

    abstract SSLContext createAcceptAnySSLContext() throws Exception;

    protected final ConnectionLogger connectionLoggerMock = mock(ConnectionLogger.class);

    @Test
    public void distrustServerSignedByUntrustedCA() {
        assertThatExceptionOfType(SSLHandshakeException.class).isThrownBy(() -> {
            try (final ServerSocket serverSocket = startServer(false)) {
                try (final Socket underTest = createSSLContext(null, HOSTNAME, null)
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                    underTest.getOutputStream().write(12);
                }
            }
        });
    }


    @Test
    public void distrustUnsignedServerHostname() {
        final String unsignedHostname = "unsigned.hostname";
        assertThatExceptionOfType(SSLHandshakeException.class).isThrownBy(() -> {
            try (final ServerSocket serverSocket = startServer(false, SERVER_WITH_ALT_NAMES);
                    final Socket underTest = createSSLContext(Certificates.CA_CRT, unsignedHostname, null)
                            .getSocketFactory()
                            .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                underTest.getOutputStream().write(137);
            }
        });
    }

    @Test
    public void distrustUnsignedServerIp() throws Exception {
        final String unsignedIPv6 = "[::126]";
        final String unsignedIPv4 = "192.168.34.12";
        try (final ServerSocket serverSocket = startServer(false, SERVER_WITH_ALT_NAMES)) {
            try (final Socket underTestV6 = createSSLContext(Certificates.CA_CRT, unsignedIPv6, null)
                    .getSocketFactory()
                    .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort());

                    final Socket underTestV4 =
                            createSSLContext(Certificates.CA_CRT, unsignedIPv4, null)
                                    .getSocketFactory()
                                    .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                assertThatExceptionOfType(SSLHandshakeException.class)
                        .isThrownBy(() -> underTestV6.getOutputStream().write(137));

                assertThatExceptionOfType(SSLHandshakeException.class)
                        .isThrownBy(() -> underTestV4.getOutputStream().write(156));
            }
        }
    }

    @Test
    public void trustUnsignedHostnameOrIpByExactCertificateMatch() throws Exception {
        final String unsignedHostname = "unsigned.hostname";
        final String unsignedIPv6 = "[::126]";
        final String serverCert = Certificates.SERVER_WITH_ALT_NAMES_CRT;
        try (final ServerSocket serverSocket = startServer(false, SERVER_WITH_ALT_NAMES)) {
            try (final Socket underTest = createSSLContext(serverCert, unsignedHostname, null)
                    .getSocketFactory()
                    .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                underTest.getOutputStream().write(29);
                assertThat(underTest.getInputStream().read()).isEqualTo(29);
            }

            try (final Socket underTest = createSSLContext(serverCert, unsignedIPv6, null)
                    .getSocketFactory()
                    .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                underTest.getOutputStream().write(177);
                assertThat(underTest.getInputStream().read()).isEqualTo(177);
            }
        }
    }

    @Test
    public void trustSignedServerDNSName() throws Exception {
        try (final ServerSocket serverSocket = startServer(false, SERVER_WITH_ALT_NAMES);
                final Socket underTest = createSSLContext(Certificates.CA_CRT, HOSTNAME, null)
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(180);
            assertThat(underTest.getInputStream().read()).isEqualTo(180);
        }
    }

    @Test(expected = SSLHandshakeException.class)
    public void distrustSignedServerCNIfSANExist() throws Exception {
        // https://tools.ietf.org/html/rfc6125#section-6.4.4
        // ... a client MUST NOT seek a match for a reference identifier of CN-ID if the presented identifiers
        // include a DNS-ID, SRV-ID, URI-ID, or any application-specific identifier types supported by the client.
        final String signedCommonName = "server.alt";
        try (final ServerSocket serverSocket = startServer(false, SERVER_WITH_ALT_NAMES);
                final Socket underTest = createSSLContext(Certificates.CA_CRT, signedCommonName, null)
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(173);
            assertThat(underTest.getInputStream().read()).isEqualTo(173);
        }
    }

    @Test
    public void trustSignedServerIp() throws Exception {
        final String signedIPv6 = "[100::1319:8a2e:370:7348]";
        final String signedIPv4 = "127.128.129.130";
        try (final ServerSocket serverSocket = startServer(false, SERVER_WITH_ALT_NAMES)) {
            try (final Socket underTest = createSSLContext(Certificates.CA_CRT, signedIPv6, null)
                    .getSocketFactory()
                    .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                underTest.getOutputStream().write(58);
                assertThat(underTest.getInputStream().read()).isEqualTo(58);
            }
            try (final Socket underTest = createSSLContext(Certificates.CA_CRT, signedIPv4, null)
                    .getSocketFactory()
                    .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

                underTest.getOutputStream().write(207);
                assertThat(underTest.getInputStream().read()).isEqualTo(207);
            }
        }
    }

    @Test
    public void distrustSelfSignedClient() throws Exception {
        try (final ServerSocket serverSocket = startServer(true, SERVER_WITH_ALT_NAMES);
                final Socket underTest = createSSLContext(Certificates.CA_CRT, HOSTNAME, SELF_SIGNED_CLIENT_CREDENTIALS)
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            // for JDK 11 < 11.0.12, an SSLException is expected:
            // for JDK 11 >= 11.0.12, an SocketException is expected:
            // both are IOExceptions:
            assertThatExceptionOfType(IOException.class).isThrownBy(() -> underTest.getOutputStream().write(234));
        }
    }

    @Test
    public void trustSignedClient() throws Exception {
        try (final ServerSocket serverSocket = startServer(true, SERVER_WITH_ALT_NAMES);
                final Socket underTest = createSSLContext(Certificates.CA_CRT, HOSTNAME, CLIENT_CREDENTIALS)
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(45);
            assertThat(underTest.getInputStream().read()).isEqualTo(45);
        }
    }

    @Test
    public void trustEverybodyWithAcceptAnyTrustManager() throws Exception {
        try (final ServerSocket serverSocket = startServer(false);
                final Socket underTest = createAcceptAnySSLContext()
                        .getSocketFactory()
                        .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {

            underTest.getOutputStream().write(67);
            assertThat(underTest.getInputStream().read()).isEqualTo(67);
        }
    }

    private ServerSocket startServer(final boolean needClientAuth) throws Exception {
        return startServer(needClientAuth, SERVER_CREDENTIALS);
    }

    private ServerSocket startServer(final boolean needClientAuth, final ClientCertificateCredentials credentials)
            throws Exception {

        final SSLServerSocket serverSocket =
                (SSLServerSocket) SSLContextCreator.of(Certificates.CA_CRT, null, HOSTNAME, connectionLoggerMock)
                        .clientCertificate(credentials)
                        .getServerSocketFactory()
                        .createServerSocket(0);

        serverSocket.setNeedClientAuth(needClientAuth);

        CompletableFuture.runAsync(() -> {
            while (true) {
                try {
                    final Socket socket = serverSocket.accept();
                    CompletableFuture.runAsync(() -> {
                        try {
                            socket.getOutputStream().write(socket.getInputStream().read());
                        } catch (final IOException e) {
                            // let the socket be closed from other end
                        }
                    });
                } catch (final IOException e) {
                    break;
                }
            }
        });

        return serverSocket;
    }

}
