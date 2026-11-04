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
package org.eclipse.ditto.internal.utils.persistence.postgres.snapshot;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;

import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;
import org.apache.pekko.persistence.snapshot.japi.SnapshotStore;

import com.typesafe.config.Config;

import scala.concurrent.Future;
import scala.jdk.javaapi.FutureConverters;

/**
 * PostgreSQL {@link SnapshotStore} — a thin japi adapter over {@link PostgresSnapshotStoreOps}
 * (which holds all the testable logic, since the japi base class forbids constructing the plugin with {@code new}).
 * <p>
 * Multi-snapshot retention: the snapshot table PK is {@code (pid, sn, written_at)}, so &gt;1 row per pid is normal
 * (Ditto actors never self-prune on save) and {@code written_at} is bound from {@link SnapshotMetadata#timestamp()}
 * (not {@code now()}).
 * </p>
 * <ul>
 *     <li><strong>{@code doLoadAsync}</strong> honours all four {@link SnapshotSelectionCriteria} bounds;
 *     {@code Long.MAX_VALUE} timestamp bounds are clamped to a representable instant.</li>
 *     <li><strong>{@code doSaveAsync}</strong> is an idempotent {@code INSERT … ON CONFLICT (pid, sn, written_at) DO
 *     UPDATE}.</li>
 *     <li><strong>Two {@code doDeleteAsync} overloads</strong> kept distinct: the metadata form deletes
 *     exactly {@code (pid, sn, written_at)}; the criteria form is upper-bounded {@code sn <= maxSeq AND written_at <=
 *     maxTs} with no lower bound.</li>
 * </ul>
 * <p>
 * The loaded JSONB is surfaced as JSON text; the snapshot adapter Pekko applies to the returned {@code SelectedSnapshot}
 * payload decodes it to a {@code JsonObject} through the provider's {@code PostgresSnapshotCodec}.
 * </p>
 */
@NotThreadSafe
public final class PostgresSnapshotStore extends SnapshotStore {

    private static final String ENTITY_KEY = "entity";

    private final PostgresSnapshotStoreOps ops;

    /**
     * Constructor invoked reflectively by Pekko's persistence-plugin loader. Pekko tries, in order, a
     * {@code (Config, String)}, then this {@code (Config)}, then a no-arg constructor when materialising a
     * snapshot-store plugin; the single-{@link Config} form receives this plugin's own config block, which carries
     * the {@code entity} table-prefix key.
     * <p>
     * The plugin resolves its entity prefix from that block and binds the shared {@link PostgresPersistenceOperations}
     * to the entity's tables, over the single per-actor-system {@link DittoPostgresClient} resolved from
     * {@link PostgresClientExtension}. The journal, snapshot-store and read-journal of a service therefore share ONE
     * connection pool instead of opening three. The shared client's lifecycle is owned by the extension, which disposes
     * it once via {@code CoordinatedShutdown}; this plugin never builds or disposes a client.
     * </p>
     *
     * @param config this snapshot-store plugin's own config block (must contain the {@code entity} key).
     */
    public PostgresSnapshotStore(final Config config) {
        final String entity = checkNotNull(config, "config").getString(ENTITY_KEY);
        final DittoPostgresClient client = PostgresClientExtension.get(context().system()).getClient();
        this.ops = PostgresSnapshotStoreOps.of(PostgresPersistenceOperations.of(client, entity));
    }

    /**
     * @param operations the shared persistence operations bound to this store's entity tables.
     */
    public PostgresSnapshotStore(final PostgresPersistenceOperations operations) {
        this.ops = PostgresSnapshotStoreOps.of(checkNotNull(operations, "operations"));
    }

    @Override
    public Future<Optional<SelectedSnapshot>> doLoadAsync(final String persistenceId,
            final SnapshotSelectionCriteria criteria) {
        return FutureConverters.asScala(ops.loadAsync(persistenceId, criteria));
    }

    @Override
    public Future<Void> doSaveAsync(final SnapshotMetadata metadata, final Object snapshot) {
        return FutureConverters.asScala(ops.saveAsync(metadata, snapshot));
    }

    @Override
    public Future<Void> doDeleteAsync(final SnapshotMetadata metadata) {
        return FutureConverters.asScala(ops.deleteAsync(metadata));
    }

    @Override
    public Future<Void> doDeleteAsync(final String persistenceId, final SnapshotSelectionCriteria criteria) {
        return FutureConverters.asScala(ops.deleteAsync(persistenceId, criteria));
    }

    // No postStop() pool disposal: the connection pool is the single shared client owned by PostgresClientExtension,
    // disposed once via CoordinatedShutdown — not per plugin actor (whose postStop Pekko does not guarantee runs before
    // dispatcher teardown).

}
