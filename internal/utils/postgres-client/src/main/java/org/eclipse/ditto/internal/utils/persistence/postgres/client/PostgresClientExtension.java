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

import java.util.concurrent.CompletionStage;

import org.apache.pekko.Done;
import org.apache.pekko.actor.AbstractExtensionId;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.Extension;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.PoolMetricsPoller;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.PostgresMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor-system {@link Extension} that owns the single, shared {@link DittoPostgresClient} (and therefore the single
 * R2DBC {@code ConnectionPool}) for a Ditto service.
 * <p>
 * This mirrors {@code MongoClientExtension} (the Mongo backend's one-client-per-actor-system seam): a Ditto service
 * persists a single entity type per actor system, and its journal, snapshot-store and read-journal plugins all talk to
 * the same PostgreSQL backend. Before this extension existed, {@code PostgresJournal(Config)}, {@code
 * PostgresSnapshotStore(Config)} and {@code PostgresPersistenceBackendProvider.getReadJournal()} each called
 * {@link DittoPostgresClient#newInstance} independently, so one service instance opened up to <strong>3 ×
 * {@code pool.max-size}</strong> connections — three times what {@code ditto-postgres-persistence.conf} documents {@code max-size}
 * to mean (the per-service total). Resolving the client through this extension collapses those three pools into one.
 * </p>
 * <p>
 * The pool is built lazily on first {@link #getClient()} (the extension instance itself is created the first time any of
 * the three plugins resolves it) and is registered <strong>once</strong> with {@link CoordinatedShutdown} so it is
 * gracefully drained on a rolling restart — closing the gap that per-plugin {@code postStop()} disposal left (Pekko does
 * not guarantee a plugin actor's {@code postStop} runs before dispatcher teardown).
 * </p>
 */
public final class PostgresClientExtension implements Extension {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresClientExtension.class);

    private static final Id EXTENSION_ID = new Id();

    private final DittoPostgresClient client;

    private PostgresClientExtension(final DittoPostgresClient client) {
        this.client = client;
    }

    /**
     * Resolves the per-actor-system shared client extension, creating (and wiring shutdown for) the single pool on first
     * access.
     *
     * @param system the actor system.
     * @return the shared extension instance for {@code system}.
     */
    public static PostgresClientExtension get(final ActorSystem system) {
        return EXTENSION_ID.get(system);
    }

    /**
     * @return the single {@link DittoPostgresClient} (one connection pool) shared across this service's
     * journal + snapshot-store + read-journal plugins.
     */
    public DittoPostgresClient getClient() {
        return client;
    }

    private static final class Id extends AbstractExtensionId<PostgresClientExtension> {

        @Override
        public PostgresClientExtension createExtension(final ExtendedActorSystem system) {
            final PostgresConfig postgresConfig =
                    DefaultPostgresConfig.of(system.settings().config().getConfig("ditto"));
            // Wire the on-demand schema self-heal into the shared client: a runtime statement hitting a missing table
            // (42P01) recreates the schema and retries, giving the Postgres backend Mongo's implicit-create property.
            // The healer's cached DDL factory is disposed by client.close() (the "dispose-shared-postgres-client"
            // CoordinatedShutdown task below), so no separate shutdown task is required.
            final PostgresSchemaHealer schemaHealer = PostgresSchemaHealer.of(postgresConfig);
            final DittoPostgresClient client = DittoPostgresClient.newInstance(postgresConfig, schemaHealer);
            // The single shared pool now exists, so surface its occupancy as gauges from here — NOT from the lazy
            // read-journal path: a write-heavy service may never call getReadJournal(), so
            // starting the poller there left the write path's pool saturation invisible. The extension is created
            // exactly once per actor system (the first time ANY of journal/snapshot/read-journal resolves the client),
            // so exactly one poller is started for the one pool, tagged pool=shared.
            final PoolMetricsPoller poller = PoolMetricsPoller.of(client::getPoolMetrics, PostgresMetrics.ROLE_SHARED);
            poller.start();
            // Register the single shared pool with CoordinatedShutdown exactly once (the extension is created exactly
            // once per actor system), so it is gracefully drained on a rolling restart rather than relying on each
            // plugin actor's postStop (which Pekko does not guarantee runs before dispatcher teardown). The poller is
            // torn down first (its shutdown-task name is derived from the pool role, so it cannot clash), then the pool.
            final CoordinatedShutdown coordinatedShutdown = CoordinatedShutdown.get(system);
            coordinatedShutdown.addTask(
                    CoordinatedShutdown.PhaseBeforeActorSystemTerminate(),
                    PoolMetricsPoller.shutdownTaskName(PostgresMetrics.ROLE_SHARED),
                    () -> {
                        poller.close();
                        return java.util.concurrent.CompletableFuture.completedFuture(Done.done());
                    });
            coordinatedShutdown.addTask(
                    CoordinatedShutdown.PhaseBeforeActorSystemTerminate(),
                    "dispose-shared-postgres-client",
                    () -> {
                        LOGGER.info("Disposing the shared PostgreSQL connection pool on coordinated shutdown.");
                        // disposeLater() is the awaitable graceful drain (vs the fire-and-forget close()); converting
                        // its Reactor Mono to a CompletionStage makes the shutdown phase wait for the drain to finish.
                        final CompletionStage<Done> done = client.disposeLater()
                                .thenReturn(Done.done())
                                .toFuture();
                        return done.exceptionally(error -> {
                            LOGGER.warn("Failed to gracefully dispose the shared PostgreSQL connection pool.", error);
                            return Done.done();
                        });
                    });
            return new PostgresClientExtension(client);
        }
    }

}
