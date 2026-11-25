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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.TcpGate;
import org.junit.Test;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.R2dbcBadGrammarException;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import io.r2dbc.spi.R2dbcPermissionDeniedException;
import io.r2dbc.spi.R2dbcRollbackException;
import io.r2dbc.spi.R2dbcTimeoutException;
import io.r2dbc.spi.R2dbcTransientResourceException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * Unit test for {@link BootstrapFailureClassifier}: what the schema bootstrap retries while PostgreSQL is unreachable,
 * and what it never retries. Synthetic chains use only public SPI types, which is faithful because the classifier never
 * matches driver class names.
 */
public final class BootstrapFailureClassifierTest {

    @Test
    public void aRealRefusedConnectIsRetried() {
        final Throwable refused = catchThrowable(() -> Mono.from(ConnectionFactories.get(
                        "r2dbc:postgresql://u:p@127.0.0.1:" + TcpGate.freePort() + "/x?sslMode=disable").create())
                .block(Duration.ofSeconds(10)));
        assertRetried(refused);
    }

    @Test
    public void transportFailuresAreRetried() {
        assertRetried(connectionError("08003", new ConnectException("Connection refused")));
        assertRetried(connectionError("08003", new UnknownHostException("postgres")));
        assertRetried(connectionError("08003", new ClosedChannelException()));
        assertRetried(connectionError("08006", new SocketException("Connection reset")));
        assertRetried(connectionError("08001", new IOException("broken pipe")));
        assertRetried(new R2dbcNonTransientResourceException("Connection unexpectedly closed", "08006"));
    }

    @Test
    public void serverNotReadyIsRetried() {
        for (final String sqlState : new String[]{"57P03", "53300", "57P01", "57P02"}) {
            assertRetried(new R2dbcNonTransientResourceException("not ready", sqlState));
        }
        assertRetried(new R2dbcTransientResourceException("too many clients", "53300"));
    }

    @Test
    public void tlsFailuresAreFatalEvenAroundAnIoException() {
        assertFatal(connectionError("08003", new SSLHandshakeException("PKIX path building failed")));
        final SSLException tlsAroundConnect = new SSLException("handshake", new ConnectException("refused"));
        assertFatal(connectionError("08003", tlsAroundConnect));
        assertFatal(connectionError("08003", new FileNotFoundException("/etc/ditto/pg-tls/ca.crt")));
    }

    @Test
    public void aSuppressedTlsErrorDoesNotDecide() {
        final ClosedChannelException closed = new ClosedChannelException();
        closed.addSuppressed(new SSLHandshakeException("handshake in progress"));
        assertRetried(connectionError("08003", closed));
    }

    @Test
    public void authenticationPermissionAndSslPolicyErrorsAreFatal() {
        assertFatal(new R2dbcPermissionDeniedException("password authentication failed", "28P01"));
        assertFatal(new R2dbcPermissionDeniedException("no pg_hba.conf entry", "28000"));
        assertFatal(new R2dbcPermissionDeniedException("permission denied", "42501"));
        assertFatal(new R2dbcPermissionDeniedException("Connection closed during SSL negotiation", "08006"));
    }

    @Test
    public void everyOtherSqlStateIsFatal() {
        for (final String sqlState : new String[]{"3D000", "3F000", "08P01", "08004", "55P03", "25006", "XX000"}) {
            assertFatal(new R2dbcNonTransientResourceException("x", sqlState));
        }
        assertFatal(new R2dbcTimeoutException("canceling statement due to statement timeout", "57014"));
        assertFatal(new R2dbcBadGrammarException("relation does not exist", "42P01"));
        assertFatal(new R2dbcRollbackException("could not serialize access", "40001"));
        assertFatal(new R2dbcNonTransientResourceException("no SQLSTATE", (String) null));
        assertFatal(new R2dbcNonTransientResourceException("wraps a whitelisted one", "XX000",
                connectionError("08003", new ConnectException("refused"))));
    }

    @Test
    public void schemaRefusalsAndAttemptTimeoutsAreFatal() {
        assertFatal(new SchemaBootException("checksum mismatch"));
        assertFatal(new RuntimeException("wrapper", new SchemaBootException("divergent primary key")));
        assertFatal(new TimeoutException("Did not observe any item"));
        assertFatal(new IllegalStateException("unknown"));
    }

    @Test
    public void compositesAreFatalIfAnyElementIs() {
        final Throwable retryable = connectionError("08003", new ConnectException("refused"));
        assertFatal(Exceptions.multiple(retryable, new SchemaBootException("drift")));
        assertRetried(Exceptions.multiple(retryable, new R2dbcNonTransientResourceException("x", "57P03")));
    }

    @Test
    public void aCyclicCauseChainTerminates() {
        final RuntimeException a = new RuntimeException("a");
        final RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertFatal(a);
    }

    private static R2dbcNonTransientResourceException connectionError(final String sqlState, final Throwable cause) {
        return new R2dbcNonTransientResourceException("Cannot connect to localhost/127.0.0.1:5432", sqlState, cause);
    }

    private static void assertRetried(final Throwable error) {
        final BootstrapFailureClassifier.Verdict verdict = BootstrapFailureClassifier.classify(error);
        assertThat(verdict.retryable()).as("retried: %s (%s)", error, verdict.reason()).isTrue();
        assertThat(verdict.reason()).startsWith("retry: ");
    }

    private static void assertFatal(final Throwable error) {
        final BootstrapFailureClassifier.Verdict verdict = BootstrapFailureClassifier.classify(error);
        assertThat(verdict.retryable()).as("fatal: %s (%s)", error, verdict.reason()).isFalse();
        assertThat(verdict.reason()).startsWith("fatal: ");
    }
}
