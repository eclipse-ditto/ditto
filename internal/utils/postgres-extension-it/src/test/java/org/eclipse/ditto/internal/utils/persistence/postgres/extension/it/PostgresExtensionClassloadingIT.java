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
package org.eclipse.ditto.internal.utils.persistence.postgres.extension.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Classloading integration test for the three-JAR layered Postgres extension packaging.
 * <p>
 * The REAL shaded extension jars are copied into {@code target/extensions} by the {@code maven-dependency-plugin} at the
 * {@code pre-integration-test} phase (see this module's pom). Each scenario builds a child {@link URLClassLoader} that
 * mimics the {@code /opt/ditto/extensions/} drop-in on top of this test's class loader — which stands in for the service
 * allinone classpath, carrying the neutral SPI interfaces ({@link PersistenceBackendProvider},
 * {@link SearchPersistenceProvider}) plus pekko/config, but NOT the extension jars (they are copied, not depended on, so
 * their provider/base classes never reach the parent — preserving the child-loader isolation).
 * <p>
 * Runs under failsafe (needs no docker): {@code mvn install} (so the extension jars are installed and copyable) then the
 * failsafe {@code integration-test} phase.
 */
public final class PostgresExtensionClassloadingIT {

    private static final String PERSISTENCE_PROVIDER =
            "org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider";
    private static final String SEARCH_PROVIDER =
            "org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchPersistenceProvider";
    // The verification-clean self-check helpers (in the thin jars) drive the same-release / missing-base check without
    // needing an ActorSystem and without triggering verification of the provider's base-referencing methods.
    private static final String PERSISTENCE_SELF_CHECK =
            "org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceExtensionSelfCheck";
    private static final String SEARCH_SELF_CHECK =
            "org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchExtensionSelfCheck";
    private static final String BASE_CLIENT_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension";
    private static final String BASE_VERSIONS_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresExtensionVersions";
    private static final String R2DBC_DRIVER_CLASS = "io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider";
    private static final String VERIFY_METHOD = "verify";

    private static File extensionsDir;

    @BeforeClass
    public static void resolveExtensionJars() {
        final String dir = System.getProperty("ditto.postgres.extensions.dir");
        assertThat(dir).as("system property ditto.postgres.extensions.dir must be set by failsafe").isNotBlank();
        extensionsDir = new File(dir);
        assertThat(baseJar()).as("base extension jar copied").exists();
        assertThat(persistenceJar()).as("persistence extension jar copied").exists();
        assertThat(searchJar()).as("search extension jar copied").exists();
    }

    private static File baseJar() {
        return new File(extensionsDir, "ditto-postgres-client-extension.jar");
    }

    private static File persistenceJar() {
        return new File(extensionsDir, "ditto-postgres-persistence-extension.jar");
    }

    private static File searchJar() {
        return new File(extensionsDir, "ditto-postgres-search-extension.jar");
    }

    private static URLClassLoader extensionLoader(final File... jars) {
        final URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            try {
                urls[i] = jars[i].toURI().toURL();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
        // Parent = this test's loader = the "service allinone-like" classpath (neutral SPI + pekko + config).
        return new URLClassLoader(urls, PostgresExtensionClassloadingIT.class.getClassLoader());
    }

    @Test
    public void basePlusPersistenceResolvesPersistenceProvider() throws Exception {
        try (final URLClassLoader loader = extensionLoader(baseJar(), persistenceJar())) {
            final Class<?> providerClass = loader.loadClass(PERSISTENCE_PROVIDER);
            // The provider implements the neutral SPI resolved from the (shared) parent loader.
            assertThat(PersistenceBackendProvider.class.isAssignableFrom(providerClass))
                    .as("PostgresPersistenceBackendProvider is a PersistenceBackendProvider").isTrue();
            // The base client infra + the shaded third-party r2dbc driver resolve through the base jar.
            assertThat(loader.loadClass(BASE_CLIENT_CLASS)).isNotNull();
            assertThat(loader.loadClass(R2DBC_DRIVER_CLASS)).isNotNull();
            // The same-release marker self-check passes (base + persistence markers, one version).
            invokeVerify(loader, PERSISTENCE_SELF_CHECK);
        }
    }

    @Test
    public void basePlusSearchResolvesSearchProvider() throws Exception {
        try (final URLClassLoader loader = extensionLoader(baseJar(), searchJar())) {
            final Class<?> providerClass = loader.loadClass(SEARCH_PROVIDER);
            assertThat(SearchPersistenceProvider.class.isAssignableFrom(providerClass))
                    .as("PostgresSearchPersistenceProvider is a SearchPersistenceProvider").isTrue();
            assertThat(loader.loadClass(BASE_CLIENT_CLASS)).isNotNull();
            assertThat(loader.loadClass(R2DBC_DRIVER_CLASS)).isNotNull();
            invokeVerify(loader, SEARCH_SELF_CHECK);
        }
    }

    @Test
    public void thinPersistenceWithoutBaseFailsWithActionableError() throws Exception {
        try (final URLClassLoader loader = extensionLoader(persistenceJar())) {
            // The provider class still LOADS (its base references resolve lazily), proving the thin jar is intact...
            assertThat(loader.loadClass(PERSISTENCE_PROVIDER)).isNotNull();
            // ...but the base is absent, so the base infra classes must NOT be resolvable through this loader.
            assertThatThrownBy(() -> loader.loadClass(BASE_CLIENT_CLASS)).isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> loader.loadClass(BASE_VERSIONS_CLASS)).isInstanceOf(ClassNotFoundException.class);
            assertThat(unwrapInvocationError(loader, PERSISTENCE_SELF_CHECK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ditto-postgres-client-extension")
                    .hasMessageContaining("ditto-postgres-persistence-extension");
        }
    }

    @Test
    public void thinSearchWithoutBaseFailsWithActionableError() throws Exception {
        try (final URLClassLoader loader = extensionLoader(searchJar())) {
            assertThat(loader.loadClass(SEARCH_PROVIDER)).isNotNull();
            assertThatThrownBy(() -> loader.loadClass(BASE_CLIENT_CLASS)).isInstanceOf(ClassNotFoundException.class);
            assertThat(unwrapInvocationError(loader, SEARCH_SELF_CHECK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ditto-postgres-client-extension")
                    .hasMessageContaining("ditto-postgres-search-extension");
        }
    }

    private static void invokeVerify(final URLClassLoader loader, final String selfCheckClass) throws Exception {
        final Method verify = loader.loadClass(selfCheckClass).getMethod(VERIFY_METHOD);
        verify.invoke(null);
    }

    private static Throwable unwrapInvocationError(final URLClassLoader loader, final String selfCheckClass)
            throws Exception {
        final Method verify = loader.loadClass(selfCheckClass).getMethod(VERIFY_METHOD);
        try {
            verify.invoke(null);
            throw new AssertionError("expected " + selfCheckClass + "." + VERIFY_METHOD
                    + "() to fail without the base extension jar");
        } catch (final InvocationTargetException e) {
            return e.getCause();
        }
    }

}
