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

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;

import reactor.core.publisher.Mono;

/**
 * Integration test proving the plancache generic-plan-flip guard (search plan §3.5 / bench-results doc §8.2) is
 * <strong>active on a real pooled connection</strong>: with {@code ditto.postgresql.force-custom-plan=true},
 * {@code SHOW plan_cache_mode} returns {@code force_custom_plan} on a connection borrowed from the r2dbc
 * {@link ConnectionPool}; without the flag it stays at PostgreSQL's default {@code auto} — proving the config flag (and
 * not the environment) is what flips it.
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an unreachable
 * Docker into a skip, not a failure).
 * </p>
 */
public final class ConnectionPoolFactoryPlanCacheModeIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping ConnectionPoolFactoryPlanCacheModeIT",
                    t);
        }
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    public void forceCustomPlanIsActiveOnPooledConnection() {
        // initial-size=1 pre-warms a connection, so the setting is proven on a genuinely pooled (borrowed) connection.
        assertThat(showPlanCacheMode(true)).isEqualTo("force_custom_plan");
    }

    @Test
    public void defaultLeavesPlanCacheModeAtPostgresDefault() {
        // Control: without the guard the pooled connection carries PostgreSQL's default, so the flag is the cause.
        assertThat(showPlanCacheMode(false)).isEqualTo("auto");
    }

    private static String showPlanCacheMode(final boolean forceCustomPlan) {
        final PostgresConfig config = configFor(forceCustomPlan);
        final ConnectionPool pool = ConnectionPoolFactory.createConnectionPool(config);
        try {
            return Mono.usingWhen(
                            pool.create(),
                            connection -> Mono.from(connection.createStatement("SHOW plan_cache_mode").execute())
                                    .flatMap(result -> Mono.from(result.map((row, meta) -> row.get(0, String.class)))),
                            Connection::close)
                    .block(Duration.ofSeconds(20));
        } finally {
            pool.disposeLater().block(Duration.ofSeconds(20));
        }
    }

    private static PostgresConfig configFor(final boolean forceCustomPlan) {
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.initial-size = 1\n"
                        + "ditto.postgresql.pool.max-size = 2\n"
                        + "ditto.postgresql.force-custom-plan = " + forceCustomPlan + "\n");
        return DefaultPostgresConfig.of(config.getConfig("ditto"));
    }

}
