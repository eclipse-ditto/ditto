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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;

/**
 * PostgreSQL implementation of {@link PersistencePluginConfig} — the Postgres mirror of
 * {@code MongoPersistencePluginConfig}. It yields the per-entity journal/snapshot plugin IDs (delegated to the
 * owning {@link PostgresPersistenceBackendProvider}), the per-service {@code pekko.persistence.*.auto-start-*} plugin IDs
 * the Postgres profile ships, and the per-entity cluster-sharding remember-store plugin IDs.
 *
 * <h2>ID-sourcing approach — constants matching the shipped Postgres profile</h2>
 * The auto-start IDs are hardcoded as named constants whose values are verified verbatim against the shipped Postgres
 * profile {@code ditto-postgres-persistence.conf} ({@code journal.auto-start-journals} /
 * {@code snapshot-store.auto-start-snapshot-stores}). Hardcoding — rather than reading the conf at runtime — keeps these
 * values available to the HOCON drift tests and to the boot-time {@link PostgresPluginWiringCheck} without a
 * fully-loaded service config, and turns a value drift into a failing unit test. Any change to the shipped IDs must
 * update both the HOCON and these constants.
 *
 * <h2>Per-entity IDs</h2>
 * {@link #getJournalPluginId(String)} / {@link #getSnapshotPluginId(String)} delegate to the
 * {@link PostgresPersistenceBackendProvider} supplied at construction time, so plugin-ID resolution is single-sourced and
 * the two surfaces (provider + this config) stay in sync — and inherit the provider's explicit, no-{@code pluralize}
 * mapping (a missing mapping is a {@code DittoConfigError}, not a fabricated ID).
 *
 * <h2>Remember-store IDs</h2>
 * As the Mongo profile runs connectivity's {@code remember-entities-store = "eventsourced"} against dedicated
 * {@code pekko-contrib-mongodb-persistence-connection-remember-*} plugins, the Postgres profile ships the dedicated
 * {@code ditto-postgres-connection-remember-*} blocks (the {@code connection_remember} table set, Pekko-serialization
 * envelope codec). {@link #getRememberStorePluginIds(String)} names them for the {@code connection} entity;
 * {@code ConnectivityRootActor} sets them on the shard region's {@code ClusterShardingSettings}. They are not
 * auto-started, as on MongoDB.
 *
 * @since 4.0.0
 */
@Immutable
public final class PostgresPersistencePluginConfig implements PersistencePluginConfig {

    // ── shipped ditto-postgres-persistence.conf block names ──────────────────
    // Verified against ditto-postgres-persistence.conf (PostgresPersistencePluginConfigTest):
    //   pekko.persistence.journal.auto-start-journals                   (things|policies|connections|wot journals)
    //   pekko.persistence.snapshot-store.auto-start-snapshot-stores     (things|policies|connections|wot snapshots)

    // things — auto-start
    static final String THINGS_JOURNAL = "ditto-postgres-things-journal";
    static final String THINGS_SNAPSHOTS = "ditto-postgres-things-snapshots";

    // policies — auto-start
    static final String POLICIES_JOURNAL = "ditto-postgres-policies-journal";
    static final String POLICIES_SNAPSHOTS = "ditto-postgres-policies-snapshots";

    // connectivity — auto-start
    static final String CONNECTIONS_JOURNAL = "ditto-postgres-connections-journal";
    static final String CONNECTIONS_SNAPSHOTS = "ditto-postgres-connections-snapshots";

    // connectivity — remember-store (the connection_remember table set; not auto-started)
    static final String CONNECTION_REMEMBER_JOURNAL = "ditto-postgres-connection-remember-journal";
    static final String CONNECTION_REMEMBER_SNAPSHOTS = "ditto-postgres-connection-remember-snapshots";

    // ── static maps built once at class-load time ─────────────────────────────

    /**
     * Per-service auto-start sets (service name → immutable list of journal + snapshot IDs). A Postgres service persists
     * a single entity type per actor system, so each service narrows to its single entity's pair — exactly as the
     * per-service config narrows the full profile list (the {@code *-pg-dev.conf} auto-start overrides).
     */
    private static final Map<String, List<String>> AUTO_START_IDS_BY_SERVICE = Map.of(
            "things",       List.of(THINGS_JOURNAL, THINGS_SNAPSHOTS),
            "policies",     List.of(POLICIES_JOURNAL, POLICIES_SNAPSHOTS),
            "connectivity", List.of(CONNECTIONS_JOURNAL, CONNECTIONS_SNAPSHOTS)
    );

    /**
     * Per-entity remember-store plugin ID pairs: the dedicated {@code connection-remember} blocks of the shipped
     * profile, verified verbatim by {@code PostgresPersistencePluginConfigTest}. See the class javadoc.
     */
    private static final Map<String, RememberStorePluginIds> REMEMBER_STORE_IDS_BY_ENTITY = Map.of(
            "connection", RememberStorePluginIds.of(CONNECTION_REMEMBER_JOURNAL, CONNECTION_REMEMBER_SNAPSHOTS)
    );

    /**
     * Delegate for per-entity journal/snapshot ID resolution. Backed by the same
     * {@link PostgresPersistenceBackendProvider} that owns this config, ensuring the two surfaces stay in sync.
     */
    private final PostgresPersistenceBackendProvider provider;

    private PostgresPersistencePluginConfig(final PostgresPersistenceBackendProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Creates a {@code PostgresPersistencePluginConfig} backed by the given provider. The provider is used for
     * per-entity journal/snapshot ID resolution so this config always returns the same IDs as the provider itself.
     *
     * @param provider the {@link PostgresPersistenceBackendProvider} that owns this config.
     * @return the new instance.
     * @throws NullPointerException if {@code provider} is {@code null}.
     */
    static PostgresPersistencePluginConfig of(final PostgresPersistenceBackendProvider provider) {
        return new PostgresPersistencePluginConfig(provider);
    }

    @Override
    public String getJournalPluginId(final String entityType) {
        return provider.getJournalPluginId(entityType);
    }

    @Override
    public String getSnapshotPluginId(final String entityType) {
        return provider.getSnapshotPluginId(entityType);
    }

    @Override
    public List<String> getAutoStartPluginIds(final String serviceName) {
        Objects.requireNonNull(serviceName, "serviceName");
        return AUTO_START_IDS_BY_SERVICE.getOrDefault(serviceName, List.of());
    }

    @Override
    public RememberStorePluginIds getRememberStorePluginIds(final String entityType) {
        Objects.requireNonNull(entityType, "entityType");
        final RememberStorePluginIds ids = REMEMBER_STORE_IDS_BY_ENTITY.get(entityType);
        if (ids == null) {
            throw new IllegalArgumentException(
                    "No remember-store plugin IDs configured for entity type '" + entityType +
                            "'. Known entities: " + REMEMBER_STORE_IDS_BY_ENTITY.keySet());
        }
        return ids;
    }
}
