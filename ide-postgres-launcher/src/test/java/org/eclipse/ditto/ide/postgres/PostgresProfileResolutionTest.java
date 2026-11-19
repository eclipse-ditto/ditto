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
package org.eclipse.ditto.ide.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;

/**
 * Pins the config the "(Postgres)" IntelliJ run configurations resolve on the classpath of this launcher module: each
 * service's own PostgreSQL journal and snapshot store, the PostgreSQL providers, no MongoDB client config, and exactly
 * one persistence backend on the classpath. No overlay is involved: the fixed-name backend includes of the service
 * confs find the PostgreSQL profiles because they are the only ones on this classpath.
 * <p>
 * The raw config is built the way a service boots: {@link RawConfigSupplier} ({@code <svc>-dev} over {@code <svc>}
 * over {@code ditto-service-base} over the reference configs, without {@code HOSTING_ENVIRONMENT}), then every
 * {@link PersistenceBackendBootstrap} on the classpath adjusts it, as {@code DittoService} does before it creates the
 * actor system. The tests assume {@code HOSTING_ENVIRONMENT} is unset, which surefire does not set.
 * </p>
 */
@RunWith(Enclosed.class)
public final class PostgresProfileResolutionTest {

    private static final String PROVIDER_CLASS_PATH = "ditto.extensions.persistence-backend-provider.extension-class";
    private static final String POSTGRES_PROVIDER_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider";
    private static final String AUTO_START_JOURNALS = "pekko.persistence.journal.auto-start-journals";
    private static final String AUTO_START_SNAPSHOTS = "pekko.persistence.snapshot-store.auto-start-snapshot-stores";
    private static final String SEARCH_PROVIDER_PATH = "ditto.extensions.search-persistence-provider";
    private static final String POSTGRES_SEARCH_PROVIDER_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchPersistenceProvider";
    private static final String QUERY_READ_PREFERENCE_PATH = "ditto.search.query.persistence.readPreference";

    private PostgresProfileResolutionTest() {
        throw new AssertionError();
    }

    /**
     * Per service: the bootstrapped config auto-starts exactly the service's own PostgreSQL plugins, selects the
     * PostgreSQL provider and has no MongoDB client config.
     */
    @RunWith(Parameterized.class)
    public static final class PerService {

        @Parameterized.Parameters(name = "{0}")
        public static List<Object[]> services() {
            return Arrays.asList(
                    new Object[]{"things", "ditto-postgres-things-journal", "ditto-postgres-things-snapshots"},
                    new Object[]{"policies", "ditto-postgres-policies-journal", "ditto-postgres-policies-snapshots"},
                    new Object[]{"connectivity", "ditto-postgres-connections-journal",
                            "ditto-postgres-connections-snapshots"});
        }

        @Parameterized.Parameter(0)
        public String serviceName;

        @Parameterized.Parameter(1)
        public String expectedJournal;

        @Parameterized.Parameter(2)
        public String expectedSnapshots;

        @Test
        public void resolvedServiceConfigSelectsTheServicesOwnPostgresPlugins() {
            final Config config = bootstrapped(RawConfigSupplier.of(serviceName).get());

            assertThat(config.getStringList(AUTO_START_JOURNALS))
                    .as("[%s] %s", serviceName, AUTO_START_JOURNALS)
                    .containsExactly(expectedJournal);
            assertThat(config.getStringList(AUTO_START_SNAPSHOTS))
                    .as("[%s] %s", serviceName, AUTO_START_SNAPSHOTS)
                    .containsExactly(expectedSnapshots);
            assertThat(config.getString(PROVIDER_CLASS_PATH))
                    .as("[%s] %s", serviceName, PROVIDER_CLASS_PATH)
                    .isEqualTo(POSTGRES_PROVIDER_CLASS);
            assertThat(config.hasPath("ditto.mongodb.uri"))
                    .as("[%s] no MongoDB client config without the MongoDB backend", serviceName)
                    .isFalse();
        }

    }

    /**
     * The launcher classpath as a whole: one persistence backend, and the search service picks the PostgreSQL search
     * provider.
     */
    public static final class Classpath {

        @Test
        public void exactlyThePostgresBackendIsOnTheClasspath() {
            assertThat(backendNames()).containsExactly("postgresql");
        }

        /**
         * Without the MongoDB client conf the optional substitution of {@code readPreference} has nothing to read, so
         * the literal default of {@code search.conf} remains.
         */
        @Test
        public void searchResolvesThePostgresSearchProvider() {
            final Config config = bootstrapped(RawConfigSupplier.of("search").get());

            assertThat(config.hasPath(SEARCH_PROVIDER_PATH)).as("%s is set", SEARCH_PROVIDER_PATH).isTrue();
            assertThat(config.getString(SEARCH_PROVIDER_PATH)).isEqualTo(POSTGRES_SEARCH_PROVIDER_CLASS);
            assertThat(config.getString(QUERY_READ_PREFERENCE_PATH)).isEqualTo("primary");
        }

    }

    private static List<String> backendNames() {
        return bootstraps().stream()
                .map(PersistenceBackendBootstrap::backendName)
                .distinct()
                .collect(Collectors.toList());
    }

    private static List<PersistenceBackendBootstrap> bootstraps() {
        return PersistenceBackendBootstrap.loadAll(PostgresProfileResolutionTest.class.getClassLoader());
    }

    /** The fold {@code DittoService.applyBootstraps} does over the raw config. */
    private static Config bootstrapped(final Config rawConfig) {
        return applyBootstraps(rawConfig, bootstraps());
    }

    private static Config applyBootstraps(final Config rawConfig, final List<PersistenceBackendBootstrap> bootstraps) {
        Config result = rawConfig;
        for (final PersistenceBackendBootstrap bootstrap : bootstraps) {
            result = bootstrap.adjustRawConfig(result);
        }
        return result;
    }

}
