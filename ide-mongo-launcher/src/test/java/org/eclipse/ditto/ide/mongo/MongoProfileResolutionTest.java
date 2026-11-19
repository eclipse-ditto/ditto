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
package org.eclipse.ditto.ide.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/**
 * Pins the config the default IntelliJ run configurations resolve on the classpath of this launcher module: each
 * service's own MongoDB journal and snapshot store, the MongoDB providers, the MongoDB URI the bootstrap injects, and
 * exactly one persistence backend on the classpath.
 * <p>
 * The raw config is built the way a service boots: {@link RawConfigSupplier} ({@code <svc>-dev} over {@code <svc>}
 * over {@code ditto-service-base} over the reference configs, without {@code HOSTING_ENVIRONMENT}), then every
 * {@link PersistenceBackendBootstrap} on the classpath adjusts it, as {@code DittoService} does before it creates the
 * actor system. The tests assume {@code HOSTING_ENVIRONMENT} is unset, which surefire does not set.
 * </p>
 */
@RunWith(Enclosed.class)
public final class MongoProfileResolutionTest {

    private static final String PROVIDER_CLASS_PATH = "ditto.extensions.persistence-backend-provider.extension-class";
    private static final String MONGO_PROVIDER_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.mongo.MongoPersistenceBackendProvider";
    private static final String AUTO_START_JOURNALS = "pekko.persistence.journal.auto-start-journals";
    private static final String AUTO_START_SNAPSHOTS = "pekko.persistence.snapshot-store.auto-start-snapshot-stores";
    private static final String MONGOURI_PATH = "pekko.contrib.persistence.mongodb.mongo.mongouri";
    private static final String SEARCH_PROVIDER_PATH = "ditto.extensions.search-persistence-provider";
    private static final String MONGO_SEARCH_PROVIDER_CLASS =
            "org.eclipse.ditto.thingsearch.persistence.mongo.MongoSearchPersistenceProvider";
    private static final String QUERY_READ_PREFERENCE_PATH = "ditto.search.query.persistence.readPreference";

    private MongoProfileResolutionTest() {
        throw new AssertionError();
    }

    /**
     * Per service: the bootstrapped config auto-starts exactly the service's own MongoDB plugins, selects the MongoDB
     * provider and carries the MongoDB URI in the Pekko plugin path.
     */
    @RunWith(Parameterized.class)
    public static final class PerService {

        @Parameterized.Parameters(name = "{0}")
        public static List<Object[]> services() {
            return Arrays.asList(
                    new Object[]{"things", "pekko-contrib-mongodb-persistence-things-journal",
                            "pekko-contrib-mongodb-persistence-things-snapshots"},
                    new Object[]{"policies", "pekko-contrib-mongodb-persistence-policies-journal",
                            "pekko-contrib-mongodb-persistence-policies-snapshots"},
                    new Object[]{"connectivity", "pekko-contrib-mongodb-persistence-connection-journal",
                            "pekko-contrib-mongodb-persistence-connection-snapshots"});
        }

        @Parameterized.Parameter(0)
        public String serviceName;

        @Parameterized.Parameter(1)
        public String expectedJournal;

        @Parameterized.Parameter(2)
        public String expectedSnapshots;

        @Test
        public void resolvedServiceConfigSelectsTheServicesOwnMongoPlugins() {
            final Config config = bootstrapped(RawConfigSupplier.of(serviceName).get());

            assertThat(config.getStringList(AUTO_START_JOURNALS))
                    .as("[%s] %s", serviceName, AUTO_START_JOURNALS)
                    .containsExactly(expectedJournal);
            assertThat(config.getStringList(AUTO_START_SNAPSHOTS))
                    .as("[%s] %s", serviceName, AUTO_START_SNAPSHOTS)
                    .containsExactly(expectedSnapshots);
            assertThat(config.getString(PROVIDER_CLASS_PATH))
                    .as("[%s] %s", serviceName, PROVIDER_CLASS_PATH)
                    .isEqualTo(MONGO_PROVIDER_CLASS);
        }

        /**
         * The plugin's own reference config may already carry a {@code mongouri}, so the path is first overwritten
         * with a sentinel: only the bootstrap can replace it with the URI of {@code ditto.mongodb}.
         */
        @Test
        public void bootstrapInjectsTheMongoUri() {
            final Config raw = RawConfigSupplier.of(serviceName).get()
                    .withValue(MONGOURI_PATH, ConfigValueFactory.fromAnyRef("sentinel"));
            final String expectedUri =
                    DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(raw)).getMongoDbUri();

            final String mongoUri = bootstrapped(raw).getString(MONGOURI_PATH);

            assertThat(mongoUri).as("[%s] %s", serviceName, MONGOURI_PATH)
                    .isNotEqualTo("sentinel")
                    .isEqualTo(expectedUri);
        }

    }

    /**
     * The launcher classpath as a whole: one persistence backend, and the search service picks the MongoDB search
     * provider.
     */
    public static final class Classpath {

        @Test
        public void exactlyTheMongoBackendIsOnTheClasspath() {
            assertThat(backendNames()).containsExactly("mongodb");
        }

        @Test
        public void searchResolvesTheMongoSearchProvider() {
            final Config config = bootstrapped(RawConfigSupplier.of("search").get());

            assertThat(config.hasPath(SEARCH_PROVIDER_PATH)).as("%s is set", SEARCH_PROVIDER_PATH).isTrue();
            assertThat(config.getString(SEARCH_PROVIDER_PATH)).isEqualTo(MONGO_SEARCH_PROVIDER_CLASS);
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
        return PersistenceBackendBootstrap.loadAll(MongoProfileResolutionTest.class.getClassLoader());
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
