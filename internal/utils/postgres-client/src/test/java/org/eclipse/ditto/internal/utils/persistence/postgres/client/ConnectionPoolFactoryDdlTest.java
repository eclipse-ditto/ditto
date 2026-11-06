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

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * Unit test proving that {@code ConnectionPoolFactory} actually consumes {@code pool.ddl-credentials} when building the
 * DDL-role connection options used by the schema bootstrap (the finding: {@code getDdlCredentials()} /
 * {@code DdlCredentialsConfig.isConfigured()} had ZERO production consumers, so {@code POSTGRES_DDL_USER}/
 * {@code POSTGRES_DDL_PASSWORD} did nothing).
 * <p>
 * Fully offline: building {@link ConnectionFactoryOptions} never opens a connection. SSL is disabled so the boot guard
 * stays quiet.
 * </p>
 */
public final class ConnectionPoolFactoryDdlTest {

    private static PostgresConfig configWith(final String ddlUser, final String ddlPassword) {
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.username = \"runtime_user\"\n"
                        + "ditto.postgresql.password = \"runtime_secret\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.ddl-credentials.username = \"" + ddlUser + "\"\n"
                        + "ditto.postgresql.pool.ddl-credentials.password = \"" + ddlPassword + "\"\n");
        return DefaultPostgresConfig.of(config.getConfig("ditto"));
    }

    @Test
    public void ddlOptionsUseDdlRoleWhenConfigured() {
        final PostgresConfig config = configWith("ditto_ddl", "ddl_secret");

        final ConnectionFactoryOptions ddlOptions = ConnectionPoolFactory.buildDdlOptions(config);

        // The DDL role wins over the runtime role -> POSTGRES_DDL_USER/PASSWORD now actually do something.
        assertThat(ddlOptions.getValue(ConnectionFactoryOptions.USER)).isEqualTo("ditto_ddl");
        assertThat(ddlOptions.getValue(ConnectionFactoryOptions.PASSWORD)).hasToString("ddl_secret");
    }

    @Test
    public void ddlOptionsFallBackToRuntimeRoleWhenDdlEmpty() {
        // Empty DDL role -> reuse the runtime role for the bootstrap (per the conf comment).
        final PostgresConfig config = configWith("", "");

        final ConnectionFactoryOptions ddlOptions = ConnectionPoolFactory.buildDdlOptions(config);

        assertThat(ddlOptions.getValue(ConnectionFactoryOptions.USER)).isEqualTo("runtime_user");
        assertThat(ddlOptions.getValue(ConnectionFactoryOptions.PASSWORD)).hasToString("runtime_secret");
    }

    @Test
    public void createDdlConnectionFactoryBuildsADdlRoleFactory() {
        final PostgresConfig config = configWith("ditto_ddl", "ddl_secret");

        // Building the factory must succeed offline (no connection attempt) and not throw the SSL boot guard.
        assertThat(ConnectionPoolFactory.createDdlConnectionFactory(config)).isNotNull();
    }
}
