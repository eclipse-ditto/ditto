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
package org.eclipse.ditto.internal.utils.persistence.postgres.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * Unit test proving {@code ConnectionPoolFactory.baseOptions} binds the L-11 statement / connect timeouts.
 * <p>
 * Fully offline: building {@link ConnectionFactoryOptions} never opens a connection.
 * <ul>
 *     <li>{@code CONNECT_TIMEOUT} is bound directly as the r2dbc SPI {@code Duration} option.</li>
 *     <li>{@code statement_timeout} is bound as a {@code statement_timeout -> <millis>} entry of the r2dbc-postgresql
 *     {@code OPTIONS} startup-parameter {@code Map<String, String>} (NOT a libpq {@code -c ...} string), so it survives
 *     PgBouncer {@code RESET} between transaction-pooled borrows.</li>
 * </ul>
 */
public final class ConnectionPoolFactoryTimeoutTest {

    private static PostgresConfig configWith(final String connectTimeout, final String statementTimeout) {
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.connect-timeout = \"" + connectTimeout + "\"\n"
                        + "ditto.postgresql.statement-timeout = \"" + statementTimeout + "\"\n");
        return DefaultPostgresConfig.of(config.getConfig("ditto"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> startupOptionsOf(final PostgresConfig config) {
        return (Map<String, String>) ConnectionPoolFactory.buildOptions(config)
                .getValue(PostgresqlConnectionFactoryProvider.OPTIONS);
    }

    @Test
    public void bindsConnectTimeoutAndStatementTimeoutOption() {
        final PostgresConfig config = configWith("5s", "60s");

        final ConnectionFactoryOptions options = ConnectionPoolFactory.buildOptions(config);

        assertThat(options.getValue(ConnectionFactoryOptions.CONNECT_TIMEOUT))
                .as("connect timeout bound directly as the SPI Duration option")
                .isEqualTo(Duration.ofSeconds(5));

        @SuppressWarnings("unchecked")
        final Map<String, String> startupOptions =
                (Map<String, String>) options.getValue(PostgresqlConnectionFactoryProvider.OPTIONS);
        assertThat(startupOptions)
                .as("statement_timeout carried as a GUC name->value startup-parameter Map (PgBouncer-RESET-safe)")
                .containsEntry("statement_timeout", "60000");
    }

    @Test
    public void statementTimeoutZeroDisablesTheStartupOption() {
        // statement-timeout = 0 -> Postgres "no timeout"; the option must be omitted, not bound as "0".
        final PostgresConfig config = configWith("5s", "0s");

        final ConnectionFactoryOptions options = ConnectionPoolFactory.buildOptions(config);

        assertThat(options.hasOption(PostgresqlConnectionFactoryProvider.OPTIONS))
                .as("no statement_timeout startup option when disabled")
                .isFalse();
    }

    @Test
    public void forceCustomPlanBindsPlanCacheModeStartupOption() {
        // The plancache generic-plan-flip guard (search plan §3.5 / bench-results §8.2): when force-custom-plan is on,
        // plan_cache_mode=force_custom_plan must ride the OPTIONS startup-GUC map (PgBouncer-RESET-safe), alongside
        // statement_timeout.
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.statement-timeout = \"60s\"\n"
                        + "ditto.postgresql.force-custom-plan = true\n");
        final PostgresConfig postgresConfig = DefaultPostgresConfig.of(config.getConfig("ditto"));

        assertThat(startupOptionsOf(postgresConfig))
                .as("plan_cache_mode=force_custom_plan carried as a startup GUC when force-custom-plan is on")
                .containsEntry("plan_cache_mode", "force_custom_plan")
                .containsEntry("statement_timeout", "60000");
    }

    @Test
    public void forceCustomPlanDefaultsOffSoNoPlanCacheModeOption() {
        // Persistence services (PK-keyed statements) must NOT pay the guard by default.
        final PostgresConfig config = configWith("5s", "60s");

        assertThat(startupOptionsOf(config))
                .as("no plan_cache_mode startup option by default (force-custom-plan defaults off)")
                .doesNotContainKey("plan_cache_mode");
    }

    @Test
    public void forceCustomPlanAloneBindsOptionEvenWhenStatementTimeoutDisabled() {
        // force-custom-plan on + statement-timeout=0: the OPTIONS map must still exist and carry only plan_cache_mode.
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.statement-timeout = \"0s\"\n"
                        + "ditto.postgresql.force-custom-plan = true\n");
        final PostgresConfig postgresConfig = DefaultPostgresConfig.of(config.getConfig("ditto"));

        assertThat(startupOptionsOf(postgresConfig))
                .as("plan_cache_mode present, statement_timeout absent when timeout disabled")
                .containsEntry("plan_cache_mode", "force_custom_plan")
                .doesNotContainKey("statement_timeout");
    }

    @Test
    public void statementTimeoutMergesWithUriOptionsInsteadOfReplacingThem() {
        // The URI's options= query parameter carries an operator-supplied startup GUC (e.g. search_path). Binding
        // statement_timeout as a bare Map.of(...) would REPLACE that parsed value wholesale instead of merging.
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://host:5432/db?options=search_path=ditto\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.statement-timeout = \"60s\"\n");
        final PostgresConfig postgresConfig = DefaultPostgresConfig.of(config.getConfig("ditto"));

        final ConnectionFactoryOptions options = ConnectionPoolFactory.buildOptions(postgresConfig);

        @SuppressWarnings("unchecked")
        final Map<String, String> startupOptions =
                (Map<String, String>) options.getValue(PostgresqlConnectionFactoryProvider.OPTIONS);
        assertThat(startupOptions)
                .as("the URI's search_path GUC must survive alongside the statement_timeout GUC")
                .containsEntry("search_path", "ditto")
                .containsEntry("statement_timeout", "60000");
    }
}
