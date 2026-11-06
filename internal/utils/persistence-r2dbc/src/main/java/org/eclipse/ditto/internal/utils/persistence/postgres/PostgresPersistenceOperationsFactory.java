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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsCollaborators;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsFactory;
import org.eclipse.ditto.internal.utils.persistence.api.operations.EntityPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.api.operations.NamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.NoOpCloseable;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresEntitiesPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresNamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;

/**
 * PostgreSQL implementation of {@link PersistenceOperationsFactory}.
 * <p>
 * For each entity type it builds the persistence-operations collaborators a service's ops actor purges with, preserving
 * the historical per-service wiring exactly (same shape as {@code MongoPersistenceOperationsFactory}):
 * <ul>
 *     <li>{@code thing} &rarr; {@link PostgresNamespacePersistenceOperations} only — mirrors
 *     {@code ThingPersistenceOperationsActor} (pid prefix {@code "thing:"}, supports namespaces),</li>
 *     <li>{@code policy} &rarr; both namespace- and entity-ops — mirrors {@code PolicyPersistenceOperationsActor}
 *     (pid prefix {@code "policy:"}, supports namespaces),</li>
 *     <li>{@code connection} &rarr; {@link PostgresEntitiesPersistenceOperations} only — mirrors
 *     {@code ConnectionPersistenceOperationsActor} (pid prefix {@code "connection:"}, does NOT support namespaces).</li>
 * </ul>
 * The collaborators run REAL SQL DELETE-by-namespace / by-entity through {@link PostgresPersistenceOperations} on the
 * single per-actor-system pool owned by {@link PostgresClientExtension}.
 * <p>
 * <strong>Closeable is a NO-OP:</strong> unlike the Mongo factory (which builds a fresh {@code MongoClientWrapper} per
 * call and hands it back as the bundle's {@code closeable} for the ops actor to close on {@code postStop}), the Postgres
 * collaborators read through the shared, externally-owned {@link DittoPostgresClient} pool. That pool is owned and
 * lifecycle-managed by {@link PostgresClientExtension} (registered with {@code CoordinatedShutdown} once per actor
 * system), NOT by the ops actor — so the bundle's {@link PersistenceOperationsCollaborators#closeable()} is a
 * {@link NoOpCloseable}. Closing the shared pool when one ops actor stops would tear the pool out from under the
 * journal, snapshot-store and read-journal plugins that share it; the empty close is therefore correct, not a stub.
 * <p>
 * <strong>Laziness:</strong> {@link #operations(String)} resolves the shared client from {@link PostgresClientExtension}
 * per call (cheap — the extension memoises the single pool), so it MUST be invoked lazily at ops-actor instantiation
 * time, matching the {@link PersistenceOperationsFactory} contract.
 *
 * @since 4.0.0
 */
@Immutable
public final class PostgresPersistenceOperationsFactory implements PersistenceOperationsFactory {

    private static final String THING = "thing";
    private static final String POLICY = "policy";
    private static final String CONNECTION = "connection";

    private final ActorSystem actorSystem;

    private PostgresPersistenceOperationsFactory(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * @param actorSystem the actor system whose {@link PostgresClientExtension} owns the single shared pool the
     * collaborators read through.
     * @return the factory.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static PostgresPersistenceOperationsFactory of(final ActorSystem actorSystem) {
        return new PostgresPersistenceOperationsFactory(Objects.requireNonNull(actorSystem, "actorSystem"));
    }

    @Override
    public PersistenceOperationsCollaborators operations(final String entityType) {
        final EntityWiring wiring = EntityWiring.forEntityType(entityType);

        // Resolve the single shared pool (PostgresClientExtension memoises it) and bind the persistence operations to
        // this entity's tables. NOT a fresh client per call (the Mongo factory's per-call client is replaced here by the
        // one externally-owned pool), so the bundle's closeable is a no-op.
        final DittoPostgresClient sharedClient = PostgresClientExtension.get(actorSystem).getClient();
        final PostgresPersistenceOperations postgresOperations =
                PostgresPersistenceOperations.of(sharedClient, wiring.tablePrefix);

        @Nullable final NamespacePersistenceOperations namespaceOps = wiring.namespaceOps
                ? PostgresNamespacePersistenceOperations.of(postgresOperations, wiring.pidPrefix)
                : null;
        @Nullable final EntityPersistenceOperations entitiesOps = wiring.entitiesOps
                ? PostgresEntitiesPersistenceOperations.of(postgresOperations, wiring.pidPrefix)
                : null;

        return PersistenceOperationsCollaborators.of(namespaceOps, entitiesOps, NoOpCloseable.getInstance());
    }

    /**
     * Per-entity wiring, kept identical to the historical per-service ops actors (and to the Mongo factory's
     * {@code EntityWiring}). {@code tablePrefix} is the plural table prefix ({@code things}/{@code policies}/
     * {@code connections}); {@code pidPrefix} is the singular persistence-id prefix ({@code thing:}/{@code policy:}/
     * {@code connection:}) — the same split the rest of the Postgres backend uses.
     */
    private enum EntityWiring {

        // pidPrefix, tablePrefix, buildNamespaceOps, buildEntitiesOps
        THING_WIRING(THING + ":", "things", true, false),
        POLICY_WIRING(POLICY + ":", "policies", true, true),
        CONNECTION_WIRING(CONNECTION + ":", "connections", false, true);

        private final String pidPrefix;
        private final String tablePrefix;
        private final boolean namespaceOps;
        private final boolean entitiesOps;

        EntityWiring(final String pidPrefix, final String tablePrefix, final boolean namespaceOps,
                final boolean entitiesOps) {
            this.pidPrefix = pidPrefix;
            this.tablePrefix = tablePrefix;
            this.namespaceOps = namespaceOps;
            this.entitiesOps = entitiesOps;
        }

        private static EntityWiring forEntityType(final String entityType) {
            switch (Objects.requireNonNull(entityType, "entityType")) {
                case THING:
                    return THING_WIRING;
                case POLICY:
                    return POLICY_WIRING;
                case CONNECTION:
                    return CONNECTION_WIRING;
                default:
                    throw new IllegalArgumentException(
                            "No Postgres persistence-operations wiring known for entity type <" + entityType + ">");
            }
        }
    }

}
