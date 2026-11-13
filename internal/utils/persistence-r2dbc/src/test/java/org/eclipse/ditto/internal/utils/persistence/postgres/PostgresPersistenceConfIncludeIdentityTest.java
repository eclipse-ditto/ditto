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

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Config-identity proof for the postgres-client conf extraction.
 * <p>
 * The shared {@code ditto.postgresql.*} client block was factored out of {@code ditto-postgres-persistence.conf} into
 * {@code ditto-postgres-client.conf} (internal/utils/postgres-client), pulled back in via
 * {@code include classpath("ditto-postgres-client")}. This test proves the MERGED config a persistence service resolves
 * is identical to when the block was inlined: the client block (uri/pool/ssl/ddl-credentials — resolved through the
 * cross-module classpath include) AND the persistence-specific block (the backend-provider extension) both resolve from
 * the single {@code ditto-postgres-persistence.conf} include.
 * </p>
 */
public final class PostgresPersistenceConfIncludeIdentityTest {

    private static Config resolvePersistenceProfile() {
        // parseResources follows `include classpath("ditto-postgres-client")` using the default includer, exactly as a
        // booting service does; resolve() applies the ${?ENV} optional overrides (absent in the test -> literal defaults).
        return ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();
    }

    @Test
    public void clientBlockResolvesThroughTheCrossModuleInclude() {
        final Config config = resolvePersistenceProfile();

        assertThat(config.getString("ditto.postgresql.uri")).isEqualTo("r2dbc:postgresql://localhost:5432/ditto");
        assertThat(config.getString("ditto.postgresql.pooler-mode")).isEqualTo("direct");
        assertThat(config.getInt("ditto.postgresql.pool.max-size")).isEqualTo(25);
        assertThat(config.getInt("ditto.postgresql.pool.fetch-size")).isEqualTo(0);
        assertThat(config.getInt("ditto.postgresql.pool.prepared-statement-cache-queries")).isEqualTo(0);
        assertThat(config.getString("ditto.postgresql.ssl.mode")).isEqualTo("verify-full");
        // ddl-credentials empty by default (reuse runtime role).
        assertThat(config.getString("ditto.postgresql.pool.ddl-credentials.username")).isEmpty();
    }

    @Test
    public void persistenceSpecificBlockStillPresentAlongsideTheIncludedClientBlock() {
        final Config config = resolvePersistenceProfile();

        // The persistence-specific half stays in ditto-postgres-persistence.conf (NOT moved).
        assertThat(config.hasPath("ditto.extensions.persistence-backend-provider")).isTrue();
        assertThat(config.getString("ditto.extensions.persistence-backend-provider.extension-class"))
                .isEqualTo("org.eclipse.ditto.internal.utils.persistence.postgres."
                        + "PostgresPersistenceBackendProvider");
        // And the client block resolved in the SAME merged config (proving the include merged, not replaced).
        assertThat(config.hasPath("ditto.postgresql.uri")).isTrue();
    }
}
