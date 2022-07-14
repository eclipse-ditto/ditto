/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotOffer;

/**
 * Adapter capable of transforming Snapshots (in {@link #toSnapshotStore(Object)}) done in an Akka PersistentActor to
 * another representation before persisting to the Snapshot-Store.
 * Also intercepts loading a Database's Snapshot representation (in {@link #fromSnapshotStore(SnapshotOffer)})
 * and is able to transform the Snapshot object before offering it to the Akka PersistentActor.
 * Implement this interface for persisting Snapshots in a DB-optimized way (e.g. in BSON format for MongoDB).
 *
 * @param <T> the domain model type to do a Snapshot for.
 */
public interface SnapshotAdapter<T> extends DittoExtensionPoint {

    /**
     * Converts a "domain model snapshot" type to the Object which should be persisted into the Snapshot-Store.
     *
     * @param snapshot the domain model type to do a Snapshot for.
     * @return the transformed Database type which should be persisted into Snapshot-Store.
     */
    Object toSnapshotStore(T snapshot);

    /**
     * Converts a "database snapshot" (directly loaded from the database) type to a domain model snapshot type.
     *
     * @param snapshotOffer the SnapshotOffer as offered from Akka Persistence including the db snapshot.
     * @return the transformed domain model type which is offered to the PersistentActor or {@code null}.
     */
    @Nullable
    T fromSnapshotStore(SnapshotOffer snapshotOffer);

    /**
     * Converts a "database selected snapshot" (directly loaded from the database) type to a domain model snapshot type.
     *
     * @param selectedSnapshot the SelectedSnapshot as offered from Akka Persistence including the db snapshot.
     * @return the transformed domain model type which is offered to the PersistentActor or {@code null}.
     */
    @Nullable
    T fromSnapshotStore(SelectedSnapshot selectedSnapshot);

    /**
     * Loads the implementation of {@code SnapshotAdapter} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SnapshotAdapter} should be loaded.
     * @param config the config the extension is configured.
     * @return the {@code SnapshotAdapter} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static <T> SnapshotAdapter<T> get(final ActorSystem actorSystem, final Config config) {

        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide signal enrichment for connectivity.
     */
    final class ExtensionId extends DittoExtensionPoint.ExtensionId<SnapshotAdapter> {

        private static final String CONFIG_KEY = "snapshot-adapter";

        private ExtensionId(final ExtensionIdConfig<SnapshotAdapter> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SnapshotAdapter> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SnapshotAdapter.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
