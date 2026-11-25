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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;

/**
 * Pins the Pekko circuit breakers of the eight PostgreSQL persistence plugins to the MongoDB plugins' values and
 * environment variables, so that an operator has one set of breaker settings for either backend.
 * <p>
 * Without a {@code circuit-breaker} block a plugin inherits Pekko's {@code journal-plugin-fallback} (10 / 10s / 30s)
 * or {@code snapshot-store-plugin-fallback} (5 / 20s / 60s). Pekko merges a plugin block over that fallback
 * ({@code Persistence.scala}, {@code getConfig(configPath).withFallback(getConfig(fallbackPath))}); this test does the
 * same in {@link #effectiveBreaker(Config, String, String)}.
 * </p>
 * <p>
 * Every config is resolved with the system environment switched off. Environment variables are supplied as root keys,
 * which is where HOCON looks up an optional substitution such as {@code ${?BREAKER_MAXTRIES}} first.
 * </p>
 */
public final class PostgresPluginCircuitBreakerConfTest {

    private static final List<String> JOURNALS = List.of("ditto-postgres-things-journal",
            "ditto-postgres-policies-journal", "ditto-postgres-connections-journal", "ditto-postgres-wot-journal",
            "ditto-postgres-connection-remember-journal");

    private static final List<String> SNAPSHOT_STORES = List.of("ditto-postgres-things-snapshots",
            "ditto-postgres-policies-snapshots", "ditto-postgres-connections-snapshots",
            "ditto-postgres-wot-snapshots", "ditto-postgres-connection-remember-snapshots");

    private static final String JOURNAL_FALLBACK = "pekko.persistence.journal-plugin-fallback";
    private static final String SNAPSHOT_FALLBACK = "pekko.persistence.snapshot-store-plugin-fallback";

    /** Every breaker variable set, with values that differ from all defaults. */
    private static final Map<String, String> ALL_BREAKER_ENV = Map.of(
            "BREAKER_MAXTRIES", "7", "BREAKER_TIMEOUT", "11s", "BREAKER_RESET", "13s",
            "SNAPSHOT_BREAKER_MAXTRIES", "8", "SNAPSHOT_BREAKER_TIMEOUT", "17s", "SNAPSHOT_BREAKER_RESET", "19s");

    /** Surefire runs in internal/utils/persistence-r2dbc; three parent hops reach the repository root. */
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
            .getParent()
            .getParent()
            .getParent();

    /** Each service: real resources dir, its pg-dev profile and the Postgres plugins it auto-starts. */
    private static final List<ServiceProfile> SERVICES = List.of(
            new ServiceProfile("things/service/src/main/resources", "things-pg-dev",
                    "ditto-postgres-things-journal", "ditto-postgres-things-snapshots"),
            new ServiceProfile("policies/service/src/main/resources", "policies-pg-dev",
                    "ditto-postgres-policies-journal", "ditto-postgres-policies-snapshots"),
            new ServiceProfile("connectivity/service/src/main/resources", "connectivity-pg-dev",
                    "ditto-postgres-connections-journal", "ditto-postgres-connections-snapshots"));

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void everyPostgresPluginDeclaresTheMongoBreakerValues() {
        final Config profile = profileWithEnv(Map.of());

        JOURNALS.forEach(plugin -> {
            softly.assertThat(profile.hasPath(plugin + ".circuit-breaker"))
                    .as("%s declares its own circuit-breaker", plugin)
                    .isTrue();
            assertBreaker(effectiveBreaker(profile, plugin, JOURNAL_FALLBACK), plugin,
                    5, Duration.ofSeconds(5), Duration.ofSeconds(3));
        });
        SNAPSHOT_STORES.forEach(plugin -> {
            softly.assertThat(profile.hasPath(plugin + ".circuit-breaker"))
                    .as("%s declares its own circuit-breaker", plugin)
                    .isTrue();
            assertBreaker(effectiveBreaker(profile, plugin, SNAPSHOT_FALLBACK), plugin,
                    5, Duration.ofSeconds(10), Duration.ofSeconds(6));
        });
    }

    @Test
    public void journalBreakersFollowTheMongoJournalEnvironmentVariables() {
        final Config profile = profileWithEnv(
                Map.of("BREAKER_MAXTRIES", "7", "BREAKER_TIMEOUT", "11s", "BREAKER_RESET", "13s"));

        JOURNALS.forEach(plugin -> assertBreaker(effectiveBreaker(profile, plugin, JOURNAL_FALLBACK), plugin,
                7, Duration.ofSeconds(11), Duration.ofSeconds(13)));
        SNAPSHOT_STORES.forEach(plugin -> assertBreaker(effectiveBreaker(profile, plugin, SNAPSHOT_FALLBACK), plugin,
                5, Duration.ofSeconds(10), Duration.ofSeconds(6)));
    }

    @Test
    public void snapshotStoreBreakersFollowTheMongoSnapshotEnvironmentVariables() {
        final Config profile = profileWithEnv(Map.of("SNAPSHOT_BREAKER_MAXTRIES", "8",
                "SNAPSHOT_BREAKER_TIMEOUT", "17s", "SNAPSHOT_BREAKER_RESET", "19s"));

        SNAPSHOT_STORES.forEach(plugin -> assertBreaker(effectiveBreaker(profile, plugin, SNAPSHOT_FALLBACK), plugin,
                8, Duration.ofSeconds(17), Duration.ofSeconds(19)));
        JOURNALS.forEach(plugin -> assertBreaker(effectiveBreaker(profile, plugin, JOURNAL_FALLBACK), plugin,
                5, Duration.ofSeconds(5), Duration.ofSeconds(3)));
    }

    /**
     * Parity with the real MongoDB plugin blocks of each service: same values with no environment, and the same values
     * again with every breaker variable set (so both read the same variable names).
     */
    @Test
    public void postgresBreakersMatchTheMongoPluginsOfTheSameEntity() {
        for (final Map<String, String> env : List.of(Map.<String, String>of(), ALL_BREAKER_ENV)) {
            final Config profile = profileWithEnv(env);
            final Config things = serviceConfWithEnv("things/service/src/main/resources/things.conf", env);
            final Config policies = serviceConfWithEnv("policies/service/src/main/resources/policies.conf", env);
            final Config connectivity =
                    serviceConfWithEnv("connectivity/service/src/main/resources/connectivity.conf", env);

            assertSameBreaker(things, "pekko-contrib-mongodb-persistence-things-journal",
                    profile, "ditto-postgres-things-journal", env);
            assertSameBreaker(things, "pekko-contrib-mongodb-persistence-things-snapshots",
                    profile, "ditto-postgres-things-snapshots", env);
            assertSameBreaker(things, "pekko-contrib-mongodb-persistence-wot-validation-config-journal",
                    profile, "ditto-postgres-wot-journal", env);
            assertSameBreaker(things, "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots",
                    profile, "ditto-postgres-wot-snapshots", env);
            assertSameBreaker(policies, "pekko-contrib-mongodb-persistence-policies-journal",
                    profile, "ditto-postgres-policies-journal", env);
            assertSameBreaker(policies, "pekko-contrib-mongodb-persistence-policies-snapshots",
                    profile, "ditto-postgres-policies-snapshots", env);
            assertSameBreaker(connectivity, "pekko-contrib-mongodb-persistence-connection-journal",
                    profile, "ditto-postgres-connections-journal", env);
            assertSameBreaker(connectivity, "pekko-contrib-mongodb-persistence-connection-snapshots",
                    profile, "ditto-postgres-connections-snapshots", env);
        }
    }

    /** Each service's real Postgres profile (its *-pg-dev.conf) starts its plugins with the Mongo breaker values. */
    @Test
    public void eachServicesPostgresProfileStartsItsPluginsWithTheMongoBreakerValues() {
        for (final ServiceProfile service : SERVICES) {
            final Config config = pgDevConfigWithEnv(service, Map.of());
            assertThat(config.getStringList("pekko.persistence.journal.auto-start-journals"))
                    .as("%s auto-starts its journal", service.pgDevResource())
                    .containsExactly(service.journal());
            assertBreaker(effectiveBreaker(config, service.journal(), JOURNAL_FALLBACK), service.journal(),
                    5, Duration.ofSeconds(5), Duration.ofSeconds(3));
            assertBreaker(effectiveBreaker(config, service.snapshots(), SNAPSHOT_FALLBACK), service.snapshots(),
                    5, Duration.ofSeconds(10), Duration.ofSeconds(6));

            final Config overridden = pgDevConfigWithEnv(service, ALL_BREAKER_ENV);
            assertBreaker(effectiveBreaker(overridden, service.journal(), JOURNAL_FALLBACK), service.journal(),
                    7, Duration.ofSeconds(11), Duration.ofSeconds(13));
            assertBreaker(effectiveBreaker(overridden, service.snapshots(), SNAPSHOT_FALLBACK), service.snapshots(),
                    8, Duration.ofSeconds(17), Duration.ofSeconds(19));
        }
    }

    private void assertBreaker(final Config breaker, final String plugin, final int maxFailures,
            final Duration callTimeout, final Duration resetTimeout) {

        softly.assertThat(breaker.getInt("max-failures")).as("%s max-failures", plugin).isEqualTo(maxFailures);
        softly.assertThat(breaker.getDuration("call-timeout")).as("%s call-timeout", plugin).isEqualTo(callTimeout);
        softly.assertThat(breaker.getDuration("reset-timeout")).as("%s reset-timeout", plugin)
                .isEqualTo(resetTimeout);
    }

    private void assertSameBreaker(final Config mongoConf, final String mongoPlugin, final Config profile,
            final String postgresPlugin, final Map<String, String> env) {

        final Config mongo = mongoConf.getConfig(mongoPlugin + ".circuit-breaker");
        final Config postgres = profile.getConfig(postgresPlugin + ".circuit-breaker");
        softly.assertThat(postgres.getInt("max-failures"))
                .as("%s vs %s max-failures, env %s", postgresPlugin, mongoPlugin, env)
                .isEqualTo(mongo.getInt("max-failures"));
        softly.assertThat(postgres.getDuration("call-timeout"))
                .as("%s vs %s call-timeout, env %s", postgresPlugin, mongoPlugin, env)
                .isEqualTo(mongo.getDuration("call-timeout"));
        softly.assertThat(postgres.getDuration("reset-timeout"))
                .as("%s vs %s reset-timeout, env %s", postgresPlugin, mongoPlugin, env)
                .isEqualTo(mongo.getDuration("reset-timeout"));
    }

    /** The plugin block with the plugin-type fallback behind it, as Pekko's Persistence extension merges them. */
    private static Config effectiveBreaker(final Config config, final String plugin, final String fallbackPath) {
        return config.getConfig(plugin).withFallback(config.getConfig(fallbackPath)).getConfig("circuit-breaker");
    }

    /** The shipped profile (with its client include) plus Pekko's reference.conf, system environment off. */
    private static Config profileWithEnv(final Map<String, String> env) {
        return ConfigFactory.parseMap(env)
                .withFallback(ConfigFactory.parseResources("ditto-postgres-persistence.conf"))
                .withFallback(ConfigFactory.defaultReference())
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false));
    }

    /**
     * The real service conf ({@code <svc>.conf}) with its includes skipped, so that only the keys set in its own body
     * remain, among them the MongoDB plugin blocks. Substitutions that pointed into skipped includes stay unresolved.
     */
    private static Config serviceConfWithEnv(final String relativePath, final Map<String, String> env) {
        final File conf = PROJECT_ROOT.resolve(relativePath).toFile();
        assertThat(conf).as("service conf %s", conf).isFile();
        final ConfigIncluder skipIncludes = new ConfigIncluder() {
            @Override
            public ConfigIncluder withFallback(final ConfigIncluder fallback) {
                return this;
            }

            @Override
            public ConfigObject include(final ConfigIncludeContext context, final String what) {
                return ConfigFactory.empty().root();
            }
        };
        return ConfigFactory.parseMap(env)
                .withFallback(ConfigFactory.parseFile(conf,
                        ConfigParseOptions.defaults().setAllowMissing(false).setIncluder(skipIncludes)))
                .resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true));
    }

    /**
     * The service's real {@code <svc>-pg-dev.conf} (and its {@code <svc>-dev.conf} include) from the service's
     * resources directory; {@code ditto-postgres-persistence.conf} comes from this module via the parent class loader.
     * Resolved like {@code ConfigFactory.load}, but with the system environment off.
     */
    private static Config pgDevConfigWithEnv(final ServiceProfile service, final Map<String, String> env) {
        final ClassLoader serviceClassLoader = serviceClassLoader(service);
        return ConfigFactory.load(serviceClassLoader,
                ConfigFactory.parseMap(env)
                        .withFallback(ConfigFactory.parseResources(serviceClassLoader,
                                service.pgDevResource() + ".conf")),
                ConfigResolveOptions.defaults().setUseSystemEnvironment(false));
    }

    private static ClassLoader serviceClassLoader(final ServiceProfile service) {
        final File resourcesDir = PROJECT_ROOT.resolve(service.resourcesDir()).toFile();
        assertThat(resourcesDir).as("service resources %s", resourcesDir).isDirectory();
        try {
            return new URLClassLoader(new URL[]{resourcesDir.toURI().toURL()},
                    Thread.currentThread().getContextClassLoader());
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot build class loader for " + resourcesDir, e);
        }
    }

    private record ServiceProfile(String resourcesDir, String pgDevResource, String journal, String snapshots) {}

}
