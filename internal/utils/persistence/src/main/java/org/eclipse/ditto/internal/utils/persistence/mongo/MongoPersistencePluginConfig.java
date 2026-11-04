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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;

/**
 * MongoDB implementation of {@link PersistencePluginConfig}: yields the per-entity journal/snapshot plugin IDs,
 * the per-service {@code pekko.persistence.*.auto-start-*} plugin IDs, and the per-entity cluster-sharding
 * remember-store plugin IDs for the MongoDB backend.
 *
 * <h2>ID-sourcing approach — constants matching the shipped HOCON</h2>
 * The auto-start and remember-store IDs are hardcoded as named constants whose values are verified verbatim
 * against the shipped service HOCON (things.conf, policies.conf, connectivity.conf). Hardcoding — rather than
 * reading from the conf at runtime — keeps these values available to the D1 validator and G2 boot self-check
 * without requiring a fully-loaded service config at startup; it also turns a value mismatch into a failing
 * unit test rather than a runtime mystery. Any change to the shipped IDs must update both the HOCON and these
 * constants together.
 *
 * <h2>Per-entity IDs</h2>
 * The {@link #getJournalPluginId(String)} / {@link #getSnapshotPluginId(String)} methods delegate to the
 * {@link MongoPersistenceBackendProvider} supplied at construction time, so plugin-ID resolution is never
 * duplicated and the two surfaces stay in sync automatically.
 *
 * @since 4.0.0
 */
@Immutable
public final class MongoPersistencePluginConfig implements PersistencePluginConfig {

    // ── shipped pekko-contrib-mongodb-persistence-* block names ──────────────
    // Verified against:
    //   things.conf:609-614  (auto-start — things journal + snapshots; WoT intentionally absent)
    //   policies.conf:291-296 (auto-start — policies journal + snapshots)
    //   connectivity.conf:1227-1232 (auto-start — connection journal + snapshots)
    //   connectivity.conf:1175-1176 (remember-store — connection-remember journal + snapshots)

    // things — auto-start (things.conf:610, 613)
    static final String THINGS_JOURNAL = "pekko-contrib-mongodb-persistence-things-journal";
    static final String THINGS_SNAPSHOTS = "pekko-contrib-mongodb-persistence-things-snapshots";

    // policies — auto-start (policies.conf:292, 295)
    static final String POLICIES_JOURNAL = "pekko-contrib-mongodb-persistence-policies-journal";
    static final String POLICIES_SNAPSHOTS = "pekko-contrib-mongodb-persistence-policies-snapshots";

    // connectivity — auto-start (connectivity.conf:1228, 1231)
    static final String CONNECTION_JOURNAL = "pekko-contrib-mongodb-persistence-connection-journal";
    static final String CONNECTION_SNAPSHOTS = "pekko-contrib-mongodb-persistence-connection-snapshots";

    // connectivity — remember-store (connectivity.conf:1175, 1176)
    static final String CONNECTION_REMEMBER_JOURNAL =
            "pekko-contrib-mongodb-persistence-connection-remember-journal";
    static final String CONNECTION_REMEMBER_SNAPSHOTS =
            "pekko-contrib-mongodb-persistence-connection-remember-snapshots";

    // ── static maps built once at class-load time ─────────────────────────────

    /**
     * Per-service auto-start sets (service name → immutable list of journal + snapshot IDs, in the order they
     * appear in the service HOCON). WoT is intentionally absent from the things set — see things.conf:609-615.
     */
    private static final Map<String, List<String>> AUTO_START_IDS_BY_SERVICE = Map.of(
            "things",       List.of(THINGS_JOURNAL, THINGS_SNAPSHOTS),
            "policies",     List.of(POLICIES_JOURNAL, POLICIES_SNAPSHOTS),
            "connectivity", List.of(CONNECTION_JOURNAL, CONNECTION_SNAPSHOTS)
    );

    /**
     * Per-entity remember-store plugin ID pairs.
     */
    private static final Map<String, RememberStorePluginIds> REMEMBER_STORE_IDS_BY_ENTITY = Map.of(
            "connection", RememberStorePluginIds.of(CONNECTION_REMEMBER_JOURNAL, CONNECTION_REMEMBER_SNAPSHOTS)
    );

    /**
     * Delegate for per-entity journal/snapshot ID resolution. Backed by the same
     * {@link MongoPersistenceBackendProvider} that owns this config, ensuring the two surfaces stay in sync.
     */
    private final MongoPersistenceBackendProvider provider;

    private MongoPersistencePluginConfig(final MongoPersistenceBackendProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Creates a {@code MongoPersistencePluginConfig} backed by the given provider.
     * The provider is used for per-entity journal/snapshot ID resolution so that the {@link #getJournalPluginId}
     * and {@link #getSnapshotPluginId} methods always return the same IDs as the provider itself.
     *
     * @param provider the {@link MongoPersistenceBackendProvider} that owns this config.
     * @return the new instance.
     * @throws NullPointerException if {@code provider} is {@code null}.
     */
    static MongoPersistencePluginConfig of(final MongoPersistenceBackendProvider provider) {
        return new MongoPersistencePluginConfig(provider);
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
