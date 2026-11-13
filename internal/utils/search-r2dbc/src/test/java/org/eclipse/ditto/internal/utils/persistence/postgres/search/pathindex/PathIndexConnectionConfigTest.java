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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link PathIndexConnectionConfig}: which URI and settings the index-maintenance connection uses, and
 * when the configured topology cannot carry a session at all.
 */
public final class PathIndexConnectionConfigTest {

    @Test
    public void directUriReplacesTheRuntimeUriAndEverythingElseIsKept() {
        final PostgresConfig base = base("transaction");

        final PostgresConfig maintenance =
                PathIndexConnectionConfig.of(base, Optional.of("r2dbc:postgresql://db-direct:5432/ditto"));

        assertThat(maintenance.getUri()).isEqualTo("r2dbc:postgresql://db-direct:5432/ditto");
        assertThat(maintenance.getUsername()).isEqualTo(base.getUsername());
        assertThat(maintenance.getPassword()).isEqualTo(base.getPassword());
        assertThat(maintenance.getPoolConfig()).isSameAs(base.getPoolConfig());
        assertThat(maintenance.getSslConfig()).isSameAs(base.getSslConfig());
        assertThat(maintenance.getConnectTimeout()).isEqualTo(base.getConnectTimeout());
        assertThat(maintenance.getPoolerMode()).isEqualTo(base.getPoolerMode());
    }

    @Test
    public void withoutDirectUriTheRuntimeUriIsUsed() {
        final PostgresConfig base = base("direct");

        assertThat(PathIndexConnectionConfig.of(base, Optional.empty()).getUri()).isEqualTo(base.getUri());
    }

    @Test
    public void noStartupStatementTimeoutAndNoPlanCacheOverride() {
        final PostgresConfig base = base("direct");
        assertThat(base.getStatementTimeout()).isEqualTo(Duration.ofSeconds(60L));

        final PostgresConfig maintenance = PathIndexConnectionConfig.of(base, Optional.empty());

        assertThat(maintenance.getStatementTimeout()).isZero();
        assertThat(maintenance.isForceCustomPlan()).isFalse();
    }

    @Test
    public void onlyTransactionPoolingWithoutDirectUriCannotHoldASession() {
        assertThat(PathIndexConnectionConfig.canHoldASession(base("direct"), Optional.empty())).isTrue();
        assertThat(PathIndexConnectionConfig.canHoldASession(base("session"), Optional.empty())).isTrue();
        assertThat(PathIndexConnectionConfig.canHoldASession(base("transaction"), Optional.empty())).isFalse();
        assertThat(PathIndexConnectionConfig.canHoldASession(base("transaction"),
                Optional.of("r2dbc:postgresql://db-direct:5432/ditto"))).isTrue();
    }

    private static PostgresConfig base(final String poolerMode) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString(
                "postgresql {\n"
                        + "  uri = \"r2dbc:postgresql://pgbouncer:6432/ditto\"\n"
                        + "  username = runtime\n"
                        + "  password = secret\n"
                        + "  pooler-mode = " + poolerMode + "\n"
                        + "  force-custom-plan = true\n"
                        + "  ssl.mode = disable\n"
                        + "}\n"));
    }

}
