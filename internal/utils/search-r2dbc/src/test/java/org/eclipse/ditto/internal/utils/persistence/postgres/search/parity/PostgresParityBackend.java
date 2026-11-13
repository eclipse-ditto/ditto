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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.parity;

import java.time.Duration;

import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemIndexProbe;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemSearchIndex;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresQueryBuilderFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.read.PostgresThingsSearchPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresSearchUpdaterFlow;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Postgres side of the parity matrix: {@code PostgresSearchUpdaterFlow} (flattener + JSONB doc row) writing into
 * a real PG16 Testcontainer, reads via {@code PostgresThingsSearchPersistence} + {@code PostgresQueryBuilderFactory},
 * wired production-shaped exactly like the module's other ITs (one shared pool with
 * {@code plan_cache_mode=force_custom_plan}, schema bootstrapped by the descriptor-driven manager, the system index
 * {@code sf_multi} built and the selective range legs enabled through the service's {@link SystemIndexProbe}).
 */
final class PostgresParityBackend implements ParityBackend {

    private final DittoPostgresClient client;
    private final PostgresThingsSearchPersistence persistence;
    private final PostgresSearchUpdaterFlow updaterFlow;
    private final PostgresQueryBuilderFactory queryBuilderFactory;

    private PostgresParityBackend(final DittoPostgresClient client,
            final PostgresThingsSearchPersistence persistence,
            final PostgresSearchUpdaterFlow updaterFlow,
            final PostgresQueryBuilderFactory queryBuilderFactory) {
        this.client = client;
        this.persistence = persistence;
        this.updaterFlow = updaterFlow;
        this.queryBuilderFactory = queryBuilderFactory;
    }

    static PostgresParityBackend start(final PostgresDbResource postgresResource, final Config config) {
        final Config pgConfig = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"" + postgresResource.getR2dbcUrl() + "\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.initial-size = 1\n"
                        + "ditto.postgresql.pool.max-size = 4\n"
                        + "ditto.postgresql.force-custom-plan = true\n");
        final DittoPostgresClient client =
                DittoPostgresClient.newInstance(DefaultPostgresConfig.of(pgConfig.getConfig("ditto")));
        PostgresSchemaManager.of(client.getConnectionPool(), PostgresSearchSchema.descriptor()).bootstrap();
        // the tables are empty, so a plain build is fine (the service builds it CONCURRENTLY after the bootstrap)
        Mono.usingWhen(Mono.from(client.getConnectionPool().create()),
                        connection -> Flux.from(connection.createStatement(
                                SystemSearchIndex.MULTI_VALUED.createStatement()).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block(Duration.ofSeconds(30));
        final SystemIndexProbe probe = SystemIndexProbe.of(SystemSearchIndex.MULTI_VALUED);
        if (!Boolean.TRUE.equals(probe.refresh(client.getConnectionPool()).block(Duration.ofSeconds(30)))) {
            throw new IllegalStateException("sf_multi is not valid after its build");
        }
        final var flow = PostgresSearchUpdaterFlow.of(client);
        // the battery runs with range legs on, as the service does once sf_multi is valid
        final var persistence = PostgresThingsSearchPersistence.of(client, probe);
        final var qbf = new PostgresQueryBuilderFactory(DefaultLimitsConfig.of(config.getConfig("ditto")));
        return new PostgresParityBackend(client, persistence, flow, qbf);
    }

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public QueryBuilderFactory queryBuilderFactory() {
        return queryBuilderFactory;
    }

    @Override
    public ThingsSearchPersistence searchPersistence() {
        return persistence;
    }

    @Override
    public SearchUpdaterFlow updaterFlow() {
        return updaterFlow;
    }

    @Override
    public void close() {
        client.close();
    }
}
