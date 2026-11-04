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

import java.util.List;
import java.util.Objects;

/**
 * The active persistence backend's Pekko plugin-ID surface, in a single backend-neutral value abstraction.
 * <p>
 * Where {@link PersistenceBackendProvider#getJournalPluginId(String)} /
 * {@link PersistenceBackendProvider#getSnapshotPluginId(String)} answer the per-entity event-journal and snapshot-store
 * plugin IDs, this richer config additionally answers the two ID groups that boot-time and operational tooling need:
 * </p>
 * <ul>
 *     <li>the {@code pekko.persistence.journal.auto-start-journals} /
 *     {@code pekko.persistence.snapshot-store.auto-start-snapshot-stores} plugin IDs a given service ships
 *     ({@link #getAutoStartPluginIds(String)}) — consumed by D1 to author/validate the {@code *-pg-dev.conf} overrides
 *     and by the G2 boot self-check; and</li>
 *     <li>the dedicated journal + snapshot plugin IDs of the cluster-sharding {@code remember-entities} event-sourced
 *     store for an entity ({@link #getRememberStorePluginIds(String)}) — consumed by D2.</li>
 * </ul>
 * <p>
 * Service and entity keys are plain {@link String}s, matching the rest of this module
 * (see {@link PersistenceBackendProvider}, which keys its plugin IDs by a {@code String entityType}). The service key is
 * a lower-case service name such as {@code "things"}, {@code "policies"} or {@code "connectivity"}; the entity key for
 * the remember-store is a lower-case entity name such as {@code "connection"}. Keeping these {@link String}s avoids
 * pulling a typed key into this neutral module and lets a backend implementation resolve them straight from HOCON.
 *
 * @since 4.0.0
 */
public interface PersistencePluginConfig {

    /**
     * @param entityType the entity type, e.g. {@code "thing"}, {@code "policy"}, {@code "connection"}
     * @return the active backend's Pekko journal plugin ID for the entity type
     */
    String getJournalPluginId(String entityType);

    /**
     * @param entityType the entity type
     * @return the active backend's Pekko snapshot plugin ID for the entity type
     */
    String getSnapshotPluginId(String entityType);

    /**
     * Returns the {@code pekko.persistence.*.auto-start-*} plugin IDs that the given service ships for the active
     * backend, i.e. the union of its {@code journal.auto-start-journals} and
     * {@code snapshot-store.auto-start-snapshot-stores} entries.
     *
     * @param serviceName the lower-case service name, e.g. {@code "things"}, {@code "policies"}, {@code "connectivity"}
     * @return the auto-start journal and snapshot-store plugin IDs for the service (never {@code null}; empty if the
     * service auto-starts no persistence plugins)
     */
    List<String> getAutoStartPluginIds(String serviceName);

    /**
     * Returns the dedicated journal + snapshot plugin IDs that back the cluster-sharding {@code remember-entities}
     * event-sourced store for the given entity (Pekko's {@code pekko.cluster.sharding.journal-plugin-id} /
     * {@code snapshot-plugin-id} for that entity's shard region).
     *
     * @param entityType the lower-case entity name whose remember-entities store is requested, e.g. {@code "connection"}
     * @return the journal + snapshot plugin IDs of the remember-entities store
     */
    RememberStorePluginIds getRememberStorePluginIds(String entityType);

    /**
     * The journal + snapshot plugin-ID pair backing a cluster-sharding {@code remember-entities} event-sourced store.
     *
     * @param journalPluginId the Pekko journal plugin ID (the sharding {@code journal-plugin-id}).
     * @param snapshotPluginId the Pekko snapshot plugin ID (the sharding {@code snapshot-plugin-id}).
     * @since 4.0.0
     */
    record RememberStorePluginIds(String journalPluginId, String snapshotPluginId) {

        /**
         * @param journalPluginId the Pekko journal plugin ID; must not be {@code null}.
         * @param snapshotPluginId the Pekko snapshot plugin ID; must not be {@code null}.
         */
        public RememberStorePluginIds {
            Objects.requireNonNull(journalPluginId, "journalPluginId");
            Objects.requireNonNull(snapshotPluginId, "snapshotPluginId");
        }

        /**
         * Creates a {@code RememberStorePluginIds} pair.
         *
         * @param journalPluginId the Pekko journal plugin ID.
         * @param snapshotPluginId the Pekko snapshot plugin ID.
         * @return the pair.
         * @throws NullPointerException if either argument is {@code null}.
         */
        public static RememberStorePluginIds of(final String journalPluginId, final String snapshotPluginId) {
            return new RememberStorePluginIds(journalPluginId, snapshotPluginId);
        }
    }
}
