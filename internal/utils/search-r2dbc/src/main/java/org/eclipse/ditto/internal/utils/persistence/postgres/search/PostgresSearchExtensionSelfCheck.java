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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import java.lang.reflect.InvocationTargetException;

/**
 * Layered-extension boot self-check for the thin Postgres <em>search</em> extension jar (§6 D2).
 * <p>
 * The thin {@code ditto-postgres-search-extension} jar carries only the search-r2dbc classes; the shared
 * {@code ditto-postgres-client-extension} base jar (r2dbc/reactor/netty runtime + postgres-client infra, including the
 * version-marker check {@code PostgresExtensionVersions}) MUST be mounted alongside it. This helper verifies that at
 * provider construction and produces an actionable error when the base is missing.
 * <p>
 * <strong>Why reflection.</strong> The base is referenced ONLY by name via {@link Class#forName}, never as a compiled
 * symbol. That keeps this tiny class verifiable and loadable even when the base jar is absent (a class that referenced a
 * base type directly would fail JVM verification with a bare {@code NoClassDefFoundError} before this guard could run).
 * So when the base is missing, {@code Class.forName} throws a caught {@link ClassNotFoundException} and we rethrow a
 * clear {@link IllegalStateException} naming the missing base jar; when the base is present we invoke its
 * {@code PostgresExtensionVersions.verifyConsistent()} and surface any same-release-mismatch error verbatim.
 *
 * @since 4.0.0
 */
public final class PostgresSearchExtensionSelfCheck {

    private static final String VERSIONS_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresExtensionVersions";
    private static final String VERIFY_METHOD = "verifyConsistent";

    private PostgresSearchExtensionSelfCheck() {
        throw new AssertionError("nope");
    }

    /**
     * Verifies the base client extension jar is present and same-release as this thin search extension jar. Public and
     * static so the classloading IT can drive it without an {@code ActorSystem}.
     *
     * @throws IllegalStateException if the base jar is missing/incomplete, or the mounted Postgres extension jars are
     * from different Ditto releases.
     */
    public static void verify() {
        final Class<?> versions;
        try {
            versions = Class.forName(VERSIONS_CLASS, true,
                    PostgresSearchExtensionSelfCheck.class.getClassLoader());
        } catch (final ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalStateException("The Postgres search extension jar (ditto-postgres-search-extension) is on "
                    + "the classpath but its required base jar 'ditto-postgres-client-extension' is missing or "
                    + "incomplete. Mount BOTH the base client extension and the thin search extension together (from the "
                    + "SAME Ditto release) in /opt/ditto/extensions/.", e);
        }
        try {
            versions.getMethod(VERIFY_METHOD).invoke(null);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException; // surface the same-release-mismatch message verbatim
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Postgres extension version self-check failed.", cause);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Postgres extension version self-check could not run.", e);
        }
    }

}
