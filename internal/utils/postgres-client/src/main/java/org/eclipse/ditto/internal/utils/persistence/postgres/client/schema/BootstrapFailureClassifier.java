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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcPermissionDeniedException;
import reactor.core.Exceptions;

/**
 * Decides whether a failed schema-bootstrap attempt is retried: only when PostgreSQL cannot be reached, or cannot
 * accept a session yet. Every other failure, and every failure it does not recognise, is fatal: under-retrying is the
 * safe direction.
 * <p>
 * Fatal rules take precedence and match anywhere in the {@code getCause()} chain (cycle-safe, at most
 * {@value #MAX_CAUSE_DEPTH} links; suppressed exceptions are not consulted, since netty and r2dbc park secondary errors
 * there): a refusal to boot ({@link SchemaBootException}), TLS ({@link SSLException}), certificate or key material,
 * authentication/permission errors ({@link R2dbcPermissionDeniedException}) and an attempt timeout
 * ({@link TimeoutException}). The retry rules then look at the <em>outermost</em> {@link R2dbcException} only, so an
 * unknown SQLSTATE that wraps a known one stays fatal: retried are the server-not-ready SQLSTATEs
 * {@link #SERVER_NOT_READY}, and the connection-failure SQLSTATEs {@link #CONNECTION_FAILURE} when an
 * {@link IOException} lies below them or when they have no cause (the driver's "connection closed"). No driver class
 * name is matched.
 * </p>
 */
final class BootstrapFailureClassifier {

    /** Server starting up / in recovery / shutting down, and no free connection slot. */
    static final Set<String> SERVER_NOT_READY = Set.of("57P03", "53300", "57P01", "57P02");

    /** Connection exceptions the driver raises for transport failures. */
    static final Set<String> CONNECTION_FAILURE = Set.of("08001", "08003", "08006");

    private static final int MAX_CAUSE_DEPTH = 32;

    private static final List<FatalRule> FATAL_RULES = List.of(
            new FatalRule("schema refused", SchemaBootException.class::isInstance),
            new FatalRule("TLS", SSLException.class::isInstance),
            new FatalRule("certificate or key", t -> t instanceof GeneralSecurityException
                    || t instanceof FileNotFoundException || t instanceof FileSystemException),
            new FatalRule("authentication or permission", R2dbcPermissionDeniedException.class::isInstance),
            new FatalRule("attempt timed out", TimeoutException.class::isInstance));

    private BootstrapFailureClassifier() {
        throw new AssertionError();
    }

    /**
     * @param error the failure of one bootstrap attempt.
     * @return the verdict with a short reason for the log ({@code "retry: …"} or {@code "fatal: …"}).
     */
    static Verdict classify(final Throwable error) {
        if (Exceptions.isMultiple(error)) {
            return classifyComposite(Exceptions.unwrapMultiple(error));
        }
        final List<Throwable> chain = causeChain(error);
        for (final FatalRule rule : FATAL_RULES) {
            for (final Throwable link : chain) {
                if (rule.matches().test(link)) {
                    return fatal(rule.label() + " (" + describe(link) + ")");
                }
            }
        }
        for (int i = 0; i < chain.size(); i++) {
            if (chain.get(i) instanceof R2dbcException r2dbcException) {
                return classifyR2dbc(r2dbcException, chain.subList(i + 1, chain.size()));
            }
        }
        return fatal("not a recognised connection failure (" + describe(error) + ")");
    }

    private static Verdict classifyR2dbc(final R2dbcException error, final List<Throwable> causes) {
        @Nullable final String sqlState = error.getSqlState();
        if (sqlState != null && SERVER_NOT_READY.contains(sqlState)) {
            return retry("server not ready, SQLSTATE " + sqlState + " (" + error.getMessage() + ")");
        }
        if (sqlState != null && CONNECTION_FAILURE.contains(sqlState)) {
            if (error.getCause() == null) {
                return retry("connection closed, SQLSTATE " + sqlState + " (" + error.getMessage() + ")");
            }
            for (final Throwable cause : causes) {
                if (cause instanceof IOException) {
                    return retry("connection failed, SQLSTATE " + sqlState + " <- " + describe(cause));
                }
            }
        }
        return fatal("SQLSTATE " + sqlState + " (" + describe(error) + ")");
    }

    private static Verdict classifyComposite(final List<Throwable> errors) {
        Verdict firstRetry = null;
        for (final Throwable element : errors) {
            final Verdict verdict = classify(element);
            if (!verdict.retryable()) {
                return verdict;
            }
            if (firstRetry == null) {
                firstRetry = verdict;
            }
        }
        return firstRetry != null ? firstRetry : fatal("empty composite failure");
    }

    private static List<Throwable> causeChain(final Throwable error) {
        final List<Throwable> chain = new ArrayList<>();
        final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable link = error; link != null && chain.size() < MAX_CAUSE_DEPTH && seen.add(link);
                link = link.getCause()) {
            chain.add(link);
        }
        return chain;
    }

    private static String describe(final Throwable error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    private static Verdict retry(final String reason) {
        return new Verdict(true, "retry: " + reason);
    }

    private static Verdict fatal(final String reason) {
        return new Verdict(false, "fatal: " + reason);
    }

    /**
     * @param retryable whether the attempt is retried.
     * @param reason a short reason for the log.
     */
    record Verdict(boolean retryable, String reason) {}

    private record FatalRule(String label, Predicate<Throwable> matches) {}

}
