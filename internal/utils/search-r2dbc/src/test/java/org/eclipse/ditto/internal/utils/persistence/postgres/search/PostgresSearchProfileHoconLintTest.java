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

import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaBootstrapper;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigUtil;

/**
 * HOCON lint over the effective {@code ditto-postgres-search.conf} profile (Phase F requirement 2) — the
 * search-specific counterpart to {@code PostgresProfileHoconLintTest} (persistence-r2dbc).
 * <p>
 * <strong>Why this is NOT a copy of the persistence lint.</strong> That test's positive anchor is the per-service
 * Pekko auto-start journal/snapshot-store IDs ({@code pekko.persistence.journal.auto-start-journals} etc.) — the
 * search backend is not a Pekko-persistence journal plugin at all (it has no journal/snapshot-store), so those config
 * paths simply do not exist for search; asserting them would either be vacuously true (path absent, no assertion run)
 * or actively wrong. The Phase F brief is explicit about this: anchor on the PROVIDER CLASS SET instead. Likewise, this
 * test does NOT assert a naive "Mongo-free" claim: {@code ditto.mongodb} legitimately remains in the loaded config —
 * {@code search.conf} ships its own Mongo defaults for when the Postgres profile is NOT opted in, and the opt-in
 * profile only SWAPS {@code search-persistence-provider}; it does not (and must not) delete unrelated Mongo config.
 * {@link #mongoDefaultsLegitimatelyRemainPresentAlongsideThePostgresProvider()} makes that a POSITIVE, documented
 * assertion rather than leaving it as an unstated assumption.
 * </p>
 * <h3>Config loading — real files, not copies</h3>
 * {@code postgres-search-lint-test.conf} (this module's test resources) is the only test-owned file, and it contains
 * nothing but two {@code include classpath(...)} directives, in the SAME order (later wins) a real
 * {@code search-pg-dev.conf} will use (Phase H):
 * <ol>
 *     <li>{@code include classpath("search")} — the REAL {@code thingsearch/service/src/main/resources/search.conf}
 *     (on this module's test classpath via the {@code ditto-thingsearch-service} test-scope dependency), carrying the
 *     Mongo-default {@code search-persistence-provider} and the {@code ditto.mongodb} block.</li>
 *     <li>{@code include classpath("ditto-postgres-search")} — the REAL, shipped opt-in profile from THIS module's own
 *     main resources, which itself pulls in {@code include classpath("ditto-postgres-client")} (postgres-client's main
 *     resources) for the shared {@code ditto.postgresql.*} client/pool/SSL defaults.</li>
 * </ol>
 * So this test proves the opt-in profile parses standalone-included on top of the search service's own defaults, using
 * the REAL production resource files at every layer.
 *
 * @since 4.0.0
 */
public final class PostgresSearchProfileHoconLintTest {

    private static final String PROVIDER_CLASS_PATH = "ditto.extensions.search-persistence-provider";
    private static final String POSTGRES_SEARCH_PROVIDER_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchPersistenceProvider";

    private static Config loadConfig() {
        return ConfigFactory.load("postgres-search-lint-test.conf");
    }

    /**
     * Positive anchor: the loaded config carries the Postgres search provider FQCN, proving the opt-in profile was
     * genuinely loaded (and overrides the search service's own Mongo default for the SAME key — a real override, not a
     * config that merely coexists).
     */
    @Test
    public void postgresSearchProviderClassIsPresentInEffectiveConfig() {
        final Config config = loadConfig();

        assertThat(config.hasPath(PROVIDER_CLASS_PATH))
                .as("Postgres search provider class path (%s) must be present", PROVIDER_CLASS_PATH)
                .isTrue();
        assertThat(config.getString(PROVIDER_CLASS_PATH))
                .as("the opt-in profile must override search.conf's Mongo default provider with the Postgres one")
                .isEqualTo(POSTGRES_SEARCH_PROVIDER_CLASS);
    }

    /**
     * The nested {@code include classpath("ditto-postgres-client")} resolves: the shared client/pool block is present
     * with real pool keys, proving the search profile is genuinely wired to a connectable client config (not just the
     * provider-class string on its own).
     */
    @Test
    public void postgresqlClientIncludeResolvesWithPoolKeys() {
        final Config config = loadConfig();

        assertThat(config.hasPath("ditto.postgresql.uri")).isTrue();
        assertThat(config.hasPath("ditto.postgresql.pool.max-size")).isTrue();
        assertThat(config.getInt("ditto.postgresql.pool.max-size"))
                .as("the shared client default (postgres-client's ditto-postgres-client.conf) must resolve; the "
                        + "search service has its own pool of this size, counted in the connection budget")
                .isEqualTo(25);
        assertThat(config.hasPath("ditto.postgresql.ssl.mode")).isTrue();
    }

    /**
     * {@code force-custom-plan} must be {@code true} for the search profile specifically (plan §3.5 / bench-results
     * §8.2 — the plancache generic-plan-flip guard the wpath-parameterized read statements need). This key does not
     * exist at all in the shared {@code ditto-postgres-client.conf} defaults (verified: no {@code force-custom-plan}
     * entry there) — it is introduced solely by {@code ditto-postgres-search.conf} itself, so its presence here proves
     * the search profile's own override layer was genuinely applied, not merely inherited from the client include.
     */
    @Test
    public void forceCustomPlanIsEnabledForSearch() {
        final Config config = loadConfig();

        assertThat(config.hasPath("ditto.postgresql.force-custom-plan")).isTrue();
        assertThat(config.getBoolean("ditto.postgresql.force-custom-plan")).isTrue();
    }

    /**
     * Documents, as a POSITIVE assertion, that {@code ditto.mongodb} legitimately remains present after loading the
     * Postgres search profile. This is deliberately the OPPOSITE of the persistence lint's "no Mongo plugin
     * class/ID" assertions: the persistence profile fully supersedes Pekko's journal/snapshot-store plugin wiring, but
     * the search profile only swaps ONE extension key ({@code search-persistence-provider}); {@code search.conf}'s own
     * Mongo connection defaults are unrelated config that the opt-in overlay must not (and does not) touch. Asserting
     * their absence would be actively WRONG for this profile, not merely superfluous — hence no "Mongo-free" assertion
     * anywhere in this test class.
     */
    @Test
    public void mongoDefaultsLegitimatelyRemainPresentAlongsideThePostgresProvider() {
        final Config config = loadConfig();

        assertThat(config.hasPath("ditto.mongodb.database"))
                .as("search.conf's own Mongo defaults must remain untouched by the opt-in Postgres overlay")
                .isTrue();
        assertThat(config.getString("ditto.mongodb.database")).isEqualTo("search");
    }

    /**
     * The search profile carries the shared {@code ditto.postgresql.bootstrap} block of the client conf (wait for an
     * unreachable PostgreSQL at start, then stop the service) and the exit code 1 of its shutdown reason.
     */
    @Test
    public void profileCarriesTheSharedSchemaBootstrapBlock() {
        final Config config = loadConfig();

        assertThat(config.getDuration("ditto.postgresql.bootstrap.attempt-timeout")).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.getBoolean("ditto.postgresql.bootstrap.retry.enabled")).isTrue();
        assertThat(config.getDuration("ditto.postgresql.bootstrap.retry.max-duration"))
                .isEqualTo(Duration.ofSeconds(60));
        assertThat(config.getBoolean("ditto.postgresql.bootstrap.exit-on-failure")).isTrue();
        assertThat(config.getInt(ConfigUtil.joinPath("pekko", "coordinated-shutdown", "reason-overrides",
                PostgresSchemaBootstrapper.BootstrapFailed.class.getName(), "exit-code"))).isEqualTo(1);
    }

}
