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

/**
 * Backend-neutral factory for the persistence-operations collaborators (namespace purge / entity purge) of an entity
 * type.
 * <p>
 * Each service's {@code RootActor} starts exactly one persistence-operations actor that performs namespace- and
 * entity-level purges against the active backend. Today that actor is produced backend-specifically: e.g.
 * {@code ConnectionPersistenceOperationsActor.props(...)} wraps a Mongo {@code MongoEntitiesPersistenceOperations}
 * (implementing {@code EntityPersistenceOperations}) inside an {@code AbstractPersistenceOperationsActor}. This factory
 * hoists the "build the ops collaborators for an entity type" step behind the {@link PersistenceBackendProvider} so a
 * {@code RootActor} can wire the ops actor without naming a backend.
 * <p>
 * The factory yields a backend-neutral {@link PersistenceOperationsCollaborators} bundle — the
 * {@code NamespacePersistenceOperations} / {@code EntityPersistenceOperations} an ops actor needs plus the
 * {@code Closeable} it must close on {@code postStop} — rather than a Pekko {@code Props}, because the ops actor types
 * live in the SERVICE modules and a factory in a backend module cannot build their {@code Props} without a Maven cycle.
 * The {@code RootActor} feeds this bundle into the existing service ops actor. The MongoDB implementation builds the
 * collaborators around a fresh {@code MongoClientWrapper}; the PostgreSQL implementation supplies its own.
 * <p>
 * <strong>Laziness:</strong> {@link #operations(String)} constructs the backend client (e.g. a
 * {@code MongoClientWrapper}) per call. It MUST therefore be invoked lazily — at ops-actor instantiation time, not when
 * the {@code Props} is built — exactly where the client is constructed today. Callers must not cache or share the
 * returned collaborators across ops actors; each ops actor owns and closes its own {@link #operations(String)} bundle.
 *
 * @since 4.0.0
 */
@FunctionalInterface
public interface PersistenceOperationsFactory {

    /**
     * Returns the persistence-operations collaborators for the given entity type — the
     * {@code NamespacePersistenceOperations} / {@code EntityPersistenceOperations} the ops actor performs namespace and
     * entity purges with, plus the {@code Closeable} resource the ops actor must close on {@code postStop}.
     * <p>
     * This builds a fresh backend client per call and so MUST be invoked lazily at ops-actor instantiation time (see the
     * laziness note on this interface).
     *
     * @param entityType the entity type, e.g. {@code "thing"}, {@code "policy"}, {@code "connection"}
     * @return the persistence-operations collaborators bundle for this entity type.
     */
    PersistenceOperationsCollaborators operations(String entityType);
}
