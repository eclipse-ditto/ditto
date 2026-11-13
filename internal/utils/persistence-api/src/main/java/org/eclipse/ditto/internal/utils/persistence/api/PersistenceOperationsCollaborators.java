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
package org.eclipse.ditto.internal.utils.persistence.api;

import java.io.Closeable;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.api.operations.EntityPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.api.operations.NamespacePersistenceOperations;

/**
 * Backend-neutral bundle of the persistence-operations collaborators (namespace purge / entity purge) of a single
 * entity type, plus the {@link Closeable} resource that owns them.
 * <p>
 * A service's {@code RootActor} starts exactly one persistence-operations actor per entity type that performs
 * namespace- and entity-level purges against the active backend. Historically each such actor was produced
 * backend-specifically (e.g. {@code ConnectionPersistenceOperationsActor.props(...)} built a Mongo
 * {@code MongoEntitiesPersistenceOperations}). This bundle hoists "build the collaborators for an entity type" behind
 * {@link PersistenceBackendProvider#operations(String)} so the ops actor can be wired without naming a backend.
 * <p>
 * Not every entity type uses both collaborators — the wiring mirrors the historical per-service ops actors exactly:
 * <ul>
 *     <li>{@code thing} &rarr; {@link #namespaceOps()} only ({@link #entitiesOps()} is {@code null}),</li>
 *     <li>{@code policy} &rarr; both {@link #namespaceOps()} and {@link #entitiesOps()},</li>
 *     <li>{@code connection} &rarr; {@link #entitiesOps()} only ({@link #namespaceOps()} is {@code null}).</li>
 * </ul>
 * The {@link #closeable()} is the backend resource the ops actor must close on {@code postStop} (Mongo: the
 * {@code MongoClientWrapper} the collaborators read through; Postgres later: a no-op closeable). Because the bundle
 * exposes only the two neutral operations interfaces and a {@link Closeable}, neither this module nor the calling
 * {@code RootActor} depends on a backend's operations classes.
 *
 * @since 4.0.0
 */
@Immutable
public final class PersistenceOperationsCollaborators {

    @Nullable private final NamespacePersistenceOperations namespaceOps;
    @Nullable private final EntityPersistenceOperations entitiesOps;
    private final Closeable closeable;

    private PersistenceOperationsCollaborators(@Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final Closeable closeable) {

        this.namespaceOps = namespaceOps;
        this.entitiesOps = entitiesOps;
        this.closeable = Objects.requireNonNull(closeable, "closeable");
    }

    /**
     * Creates a new bundle of persistence-operations collaborators.
     *
     * @param namespaceOps the namespace-purge collaborator, or {@code null} if the entity type does not support
     * namespace operations (e.g. {@code connection}).
     * @param entitiesOps the entity-purge collaborator, or {@code null} if the entity type does not support entity
     * operations (e.g. {@code thing}).
     * @param closeable the backend resource the ops actor must close on {@code postStop} (never {@code null}; for a
     * backend without a closeable resource pass a no-op {@code Closeable}).
     * @return the bundle.
     * @throws NullPointerException if {@code closeable} is {@code null}.
     */
    public static PersistenceOperationsCollaborators of(@Nullable final NamespacePersistenceOperations namespaceOps,
            @Nullable final EntityPersistenceOperations entitiesOps,
            final Closeable closeable) {

        return new PersistenceOperationsCollaborators(namespaceOps, entitiesOps, closeable);
    }

    /**
     * @return the namespace-purge collaborator, or {@code null} if the entity type does not support namespace
     * operations.
     */
    @Nullable
    public NamespacePersistenceOperations namespaceOps() {
        return namespaceOps;
    }

    /**
     * @return the entity-purge collaborator, or {@code null} if the entity type does not support entity operations.
     */
    @Nullable
    public EntityPersistenceOperations entitiesOps() {
        return entitiesOps;
    }

    /**
     * @return the backend resource the ops actor must close on {@code postStop} (never {@code null}).
     */
    public Closeable closeable() {
        return closeable;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PersistenceOperationsCollaborators that = (PersistenceOperationsCollaborators) o;
        return Objects.equals(namespaceOps, that.namespaceOps) &&
                Objects.equals(entitiesOps, that.entitiesOps) &&
                Objects.equals(closeable, that.closeable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespaceOps, entitiesOps, closeable);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "namespaceOps=" + namespaceOps +
                ", entitiesOps=" + entitiesOps +
                ", closeable=" + closeable +
                ']';
    }

}
