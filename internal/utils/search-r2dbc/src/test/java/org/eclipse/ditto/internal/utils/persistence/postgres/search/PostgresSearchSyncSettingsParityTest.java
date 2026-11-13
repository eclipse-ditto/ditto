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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.eclipse.ditto.thingsearch.service.common.config.DefaultUpdaterConfig;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigResolveOptions;

/**
 * Pins that the updater / background-sync settings the system-test {@code sync-*} modules drive through env vars
 * reach the search service's {@link UpdaterConfig} unchanged when the Postgres search profile is layered on top of
 * {@code search.conf}: the opt-in {@code ditto-postgres-search.conf} neither defines nor shadows any
 * {@code ditto.search.*} key, so all of these knobs are consumed by the backend-neutral updater layer exactly as on
 * Mongo. Pure config test — no Docker, no actor system.
 */
public final class PostgresSearchSyncSettingsParityTest {

    private static final ConfigResolveOptions NO_SYSTEM_ENV =
            ConfigResolveOptions.defaults().setUseSystemEnvironment(false);

    /** The env the workflow exports for sync-tags-streaming-enabled, with distinct retry delays to tell them apart. */
    private static final Map<String, String> SYNC_MODULE_ENV = Map.of(
            "EVENT_PROCESSING_ACTIVE", "false",
            "BACKGROUND_SYNC_ENABLED", "true",
            "BACKGROUND_SYNC_QUIET_PERIOD", "1s",
            "BACKGROUND_SYNC_TOLERANCE_WINDOW", "1ms",
            "THINGS_SEARCH_UPDATER_STREAM_WRITE_INTERVAL", "1s",
            "THINGS_SEARCH_UPDATER_STREAM_POLICY_CACHE_RETRY_DELAY", "2s",
            "THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY", "3s");

    private static Config layeredUnresolved() {
        // service-base + search.conf + ditto-postgres-search, exactly the production layering (see the lint test)
        return ConfigFactory.parseResources("postgres-search-lint-test.conf")
                .withFallback(ConfigFactory.defaultReference());
    }

    @Test
    public void postgresSearchProfileDefinesNoSearchServiceKeys() {
        final Config overlay = ConfigFactory.parseResources("ditto-postgres-search.conf");

        // root().get(...) is a plain map lookup and works on the unresolved overlay (its ${?POSTGRES_*} are open)
        assertThat(overlay.root().keySet()).containsExactlyInAnyOrder("ditto", "pekko");
        final ConfigObject pekko = (ConfigObject) overlay.root().get("pekko");
        assertThat(pekko.keySet())
                .as("outside ditto.*, the Postgres client config only sets the exit code of its own shutdown reason")
                .containsExactly("coordinated-shutdown");
        assertThat(((ConfigObject) pekko.get("coordinated-shutdown")).keySet()).containsExactly("reason-overrides");
        final ConfigObject ditto = (ConfigObject) overlay.root().get("ditto");
        assertThat(ditto.keySet())
                .as("the opt-in profile may only swap the provider and carry Postgres client config")
                .containsExactlyInAnyOrder("extensions", "postgresql");
        assertThat(((ConfigObject) ditto.get("extensions")).keySet())
                .containsExactly("search-persistence-provider");
    }

    @Test
    public void searchConfDefaultsSurviveThePostgresProfile() {
        final UpdaterConfig updaterConfig =
                DefaultUpdaterConfig.of(layeredUnresolved().resolve(NO_SYSTEM_ENV).getConfig("ditto.search"));

        assertThat(updaterConfig.isEventProcessingActive()).isTrue();
        assertThat(updaterConfig.getBackgroundSyncConfig().isEnabled()).isTrue();
        assertThat(updaterConfig.getBackgroundSyncConfig().getQuietPeriod()).isEqualTo(Duration.ofMinutes(8L));
        assertThat(updaterConfig.getBackgroundSyncConfig().getToleranceWindow()).isEqualTo(Duration.ofMinutes(20L));
        assertThat(updaterConfig.getStreamConfig().getWriteInterval()).isEqualTo(Duration.ofSeconds(1L));
        assertThat(updaterConfig.getStreamConfig().getPolicyCacheConfig().getRetryDelay())
                .isEqualTo(Duration.ofSeconds(1L));
        assertThat(updaterConfig.getStreamConfig().getThingCacheConfig().getRetryDelay())
                .isEqualTo(Duration.ofSeconds(1L));
    }

    @Test
    public void syncModuleEnvOverridesReachTheUpdaterConfigWithThePostgresProfileLoaded() {
        // env-var names as root keys: that is exactly what search.conf's ${?ENV_VAR} substitutions look up
        final Config resolved = ConfigFactory.parseMap(SYNC_MODULE_ENV)
                .withFallback(layeredUnresolved())
                .resolve(NO_SYSTEM_ENV);

        final UpdaterConfig updaterConfig = DefaultUpdaterConfig.of(resolved.getConfig("ditto.search"));

        assertThat(updaterConfig.isEventProcessingActive()).isFalse();
        assertThat(updaterConfig.getBackgroundSyncConfig().isEnabled()).isTrue();
        assertThat(updaterConfig.getBackgroundSyncConfig().getQuietPeriod()).isEqualTo(Duration.ofSeconds(1L));
        assertThat(updaterConfig.getBackgroundSyncConfig().getToleranceWindow()).isEqualTo(Duration.ofMillis(1L));
        assertThat(updaterConfig.getStreamConfig().getWriteInterval()).isEqualTo(Duration.ofSeconds(1L));
        assertThat(updaterConfig.getStreamConfig().getPolicyCacheConfig().getRetryDelay())
                .isEqualTo(Duration.ofSeconds(2L));
        assertThat(updaterConfig.getStreamConfig().getThingCacheConfig().getRetryDelay())
                .as("requires the search.conf thing-cache binding added in Step 2")
                .isEqualTo(Duration.ofSeconds(3L));
    }

}
