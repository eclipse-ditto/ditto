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

import java.util.Objects;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

/**
 * Extension point for pluggable persistence backends (MongoDB, PostgreSQL, ...).
 * <p>
 * A provider yields the backend-specific Pekko persistence plugin IDs for a given entity
 * type (e.g. {@code "thing"}, {@code "policy"}, {@code "connection"}) and a backend-agnostic
 * {@link DittoReadJournal} used by Ditto's cleanup, namespace-ops, and historical-query code.
 * <p>
 * The provider is selected at runtime via the Ditto extension config path
 * {@code ditto.extensions.persistence-backend-provider}.
 * <p>
 * Beyond plugin IDs and the read journal, the provider also exposes the backend's collaborator
 * accessors — {@link #pluginConfig()}, {@link #operations(String)}, {@link #healthCheck()},
 * {@link #streaming(String)} and {@link #snapshotCodec()} — including the factory for backend-specific
 * namespace persistence operations. These accessors default to throwing {@link UnsupportedOperationException}
 * so a backend can wire them incrementally; each backend overrides them as it is implemented.
 *
 * @since 4.0.0
 */
public interface PersistenceBackendProvider extends DittoExtensionPoint {

    /**
     * @param entityType the entity type, e.g. {@code "thing"}, {@code "policy"}, {@code "connection"}
     * @return the Pekko journal plugin ID for the entity type, as configured in HOCON
     */
    String getJournalPluginId(String entityType);

    /**
     * @param entityType the entity type
     * @return the Pekko snapshot plugin ID for the entity type, as configured in HOCON
     */
    String getSnapshotPluginId(String entityType);

    /**
     * @return the backend's read journal instance, shared across actors in this service
     */
    DittoReadJournal getReadJournal();

    /**
     * Returns the backend family this provider belongs to. This is the backend-NEUTRAL family indicator the boot-time
     * self-check ({@link PersistenceBackendSelfCheck}) uses together with {@link #expectedPluginClassNames()} and
     * {@link #expectedReadJournalClassName()} to assert the effective config's plugin/read-journal classes match the
     * selected provider. The Mongo provider returns {@link BackendFamily#MONGODB}, the Postgres provider returns
     * {@link BackendFamily#POSTGRESQL}.
     * <p>
     * The default throws {@link UnsupportedOperationException} so existing backends compile before they wire it.
     *
     * @return the backend family.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default BackendFamily backendFamily() {
        throw new UnsupportedOperationException("backendFamily() is not implemented by this persistence backend yet");
    }

    /**
     * Returns the set of fully-qualified Pekko persistence plugin class names (the {@code class =} values in the
     * shipped journal/snapshot plugin HOCON blocks) that legitimately belong to this provider's backend. The boot-time
     * {@link PersistenceBackendSelfCheck} asserts every resolved journal/snapshot plugin {@code class} is one of these.
     * The Mongo provider returns the {@code pekko.contrib.persistence.mongodb.*} journal/snapshot class names; the
     * Postgres provider returns the {@code ...persistence.postgres.*} journal/snapshot class names.
     * <p>
     * Expressed as STRINGS (never class literals) so the persistence-api module stays backend-neutral. The default
     * throws {@link UnsupportedOperationException} so existing backends compile before they wire it.
     *
     * @return the expected journal/snapshot plugin class names for this backend.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default java.util.Set<String> expectedPluginClassNames() {
        throw new UnsupportedOperationException(
                "expectedPluginClassNames() is not implemented by this persistence backend yet");
    }

    /**
     * Returns the fully-qualified class name of this provider's read-journal implementation (the runtime class of
     * {@link #getReadJournal()}). The boot-time {@link PersistenceBackendSelfCheck} asserts the resolved read journal's
     * class name equals this value. The Mongo provider returns the Ditto {@code MongoReadJournal} class name, the
     * Postgres provider returns the {@code PostgresReadJournal} class name.
     * <p>
     * Expressed as a STRING (never a class literal) so the persistence-api module stays backend-neutral. The default
     * throws {@link UnsupportedOperationException} so existing backends compile before they wire it.
     *
     * @return the expected read-journal class name for this backend.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default String expectedReadJournalClassName() {
        throw new UnsupportedOperationException(
                "expectedReadJournalClassName() is not implemented by this persistence backend yet");
    }

    /**
     * Returns the active backend's richer plugin-ID surface: the same per-entity journal/snapshot IDs as
     * {@link #getJournalPluginId(String)} / {@link #getSnapshotPluginId(String)} <em>plus</em> the per-service
     * {@code auto-start-*} plugin IDs and the per-entity cluster-sharding remember-store plugin IDs. Consumed by the
     * HOCON authoring/validation (D1/D2) and the boot self-check (G2).
     * <p>
     * Additive to the existing per-entity getters, which stay as-is; this does not replace or re-route them.
     * <p>
     * The default throws {@link UnsupportedOperationException} so existing backends compile before they wire it: the
     * Mongo provider implements it in B1, the Postgres provider in E2.
     *
     * @return the active backend's plugin-ID config.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default PersistencePluginConfig pluginConfig() {
        throw new UnsupportedOperationException("pluginConfig() is not implemented by this persistence backend yet");
    }

    /**
     * Returns the backend-neutral {@link PersistenceOperationsCollaborators} for the given entity type — the
     * {@code NamespacePersistenceOperations} / {@code EntityPersistenceOperations} a service's persistence-operations
     * actor purges with, plus the {@code Closeable} that actor must close on {@code postStop}. A service's
     * {@code RootActor} (C2) feeds this bundle into the existing ops actor, so the {@code RootActor} stays
     * backend-agnostic.
     * <p>
     * This is the "factory for backend-specific namespace persistence operations" foreshadowed in this interface's
     * class javadoc; a backend may implement it directly or delegate to a {@link PersistenceOperationsFactory}. The
     * default throws {@link UnsupportedOperationException} so existing backends compile before they wire it: the Mongo
     * provider implements it in B2, the Postgres provider in E2.
     * <p>
     * <strong>Laziness:</strong> the implementation builds a fresh backend client per call, so this MUST be invoked
     * lazily at ops-actor instantiation time (not at {@code Props}-build time), and the returned bundle must not be
     * cached or shared across ops actors.
     *
     * @param entityType the entity type, e.g. {@code "thing"}, {@code "policy"}, {@code "connection"}
     * @return the persistence-operations collaborators bundle for the entity type.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default PersistenceOperationsCollaborators operations(final String entityType) {
        throw new UnsupportedOperationException("operations(entityType) is not implemented by this persistence backend yet");
    }

    /**
     * Returns the {@link Props} of the backend's persistence health-check actor, which a service's {@code RootActor}
     * starts to surface backend connectivity/availability into Ditto's health subsystem.
     * <p>
     * The default throws {@link UnsupportedOperationException} so existing backends compile before they wire it: the
     * Mongo provider implements it in B3, the Postgres provider in E2.
     *
     * @return the {@link Props} of the backend's health-check actor.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default Props healthCheck() {
        throw new UnsupportedOperationException("healthCheck() is not implemented by this persistence backend yet");
    }

    /**
     * Returns the {@link Props} of the backend's snapshot-streaming actor for the given entity type — the actor that
     * streams persisted snapshots (used by cleanup and historical-query flows) against the active backend.
     * <p>
     * The caller (a service's {@code RootActor}, via its per-service streaming-actor creator) supplies the
     * service-specific PID&lt;-&gt;entity-id mapping functions; the provider supplies the backend-specific read journal
     * and the {@link java.io.Closeable} resource the streaming actor closes on stop. The returned {@link Props} is the
     * backend-neutral {@code SnapshotStreamingActor} wired with those backend collaborators.
     * <p>
     * The default throws {@link UnsupportedOperationException} so existing backends compile before they wire it: the
     * Mongo provider implements it in B4, the Postgres provider in E2.
     *
     * @param entityType the entity type, e.g. {@code "thing"}, {@code "policy"}, {@code "connection"}
     * @param pid2EntityId function mapping a persistence ID to its entity ID (service-specific).
     * @param entityId2Pid function mapping an entity ID to its persistence ID (service-specific).
     * @return the {@link Props} of the backend's snapshot-streaming actor for the entity type.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default Props streaming(final String entityType,
            final java.util.function.Function<String, org.eclipse.ditto.base.model.entity.id.EntityId> pid2EntityId,
            final java.util.function.Function<org.eclipse.ditto.base.model.entity.id.EntityId, String> entityId2Pid) {
        throw new UnsupportedOperationException("streaming(entityType, ...) is not implemented by this persistence backend yet");
    }

    /**
     * Returns the backend's snapshot storage-envelope codec (Mongo = BSON, Postgres = JSONB) that wraps the shared
     * {@code SnapshotSerializer} for the active backend.
     * <p>
     * The default throws {@link UnsupportedOperationException} so existing backends compile before they wire it: the
     * Mongo provider implements it in B5, the Postgres provider in E2.
     *
     * @return the backend's snapshot codec.
     * @throws UnsupportedOperationException until the active backend overrides this accessor.
     */
    default SnapshotCodec snapshotCodec() {
        throw new UnsupportedOperationException("snapshotCodec() is not implemented by this persistence backend yet");
    }

    /**
     * Bootstraps the persistence backend's schema, if any, before any persistent actor starts writing.
     * <p>
     * This is the single production entry point a service's {@code RootActor} calls exactly once during boot,
     * <em>before</em> {@link #getReadJournal()} and before the persistent-actor shard regions are started. A backend
     * with a managed, code-owned schema (e.g. PostgreSQL) creates and verifies its tables here so a fresh database is
     * usable on first journal write rather than failing with {@code relation does not exist}. The call blocks until
     * the schema is established. While the database is not reachable yet, it may wait for a bounded,
     * backend-configured time (PostgreSQL: {@code ditto.postgresql.bootstrap}); it MUST throw on any other failure and
     * when that wait is exhausted, so a service refuses to serve traffic against a database whose tables are absent
     * and were never bootstrapped.
     * </p>
     * <p>
     * The default implementation is a no-op: backends whose schema is managed by the storage engine itself (e.g.
     * MongoDB, which creates collections lazily on first write) need no bootstrap and inherit this no-op. Only a
     * backend with an explicit DDL schema overrides it. Because the call goes through this interface, the {@code
     * RootActor}s remain backend-agnostic — Mongo deployments invoke a harmless no-op, Postgres deployments run the
     * real bootstrap.
     * </p>
     *
     * @throws RuntimeException if the schema cannot be bootstrapped or verified (boot must fail).
     */
    default void bootstrapSchema() {
        // No-op: the default backend (MongoDB) manages its own schema lazily and needs no DDL bootstrap.
    }

    /**
     * Loads the configured {@code PersistenceBackendProvider} for the actor system.
     *
     * @param actorSystem the actor system in which the provider should be loaded
     * @param config the configuration the extension is loaded from
     * @return the configured provider instance
     */
    static PersistenceBackendProvider get(final ActorSystem actorSystem, final Config config) {
        Objects.requireNonNull(actorSystem, "actorSystem");
        Objects.requireNonNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    /**
     * Extension ID for the persistence backend provider.
     */
    final class ExtensionId extends DittoExtensionPoint.ExtensionId<PersistenceBackendProvider> {

        private static final String CONFIG_KEY = "persistence-backend-provider";

        private ExtensionId(final ExtensionIdConfig<PersistenceBackendProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<PersistenceBackendProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(PersistenceBackendProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
