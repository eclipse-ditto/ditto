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
package org.eclipse.ditto.internal.utils.persistence.api.serializer;

import javax.annotation.Nullable;

import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotOffer;

/**
 * Adapter capable of transforming Snapshots (in {@link #toSnapshotStore(Object)}) done in an Pekko PersistentActor to
 * another representation before persisting to the Snapshot-Store.
 * Also intercepts loading a Database's Snapshot representation (in {@link #fromSnapshotStore(SnapshotOffer)})
 * and is able to transform the Snapshot object before offering it to the Pekko PersistentActor.
 * <p>
 * The single neutral implementation is {@link NeutralSnapshotAdapter}, which composes the per-service
 * {@link SnapshotSerializer} (selected via {@code ditto.extensions.snapshot-adapter}) with the active backend's
 * {@link org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec} (BSON for Mongo, JSONB for Postgres). It is
 * resolved once, single-per-JVM, by {@code AbstractPersistenceActor}; there is no per-backend snapshot-adapter
 * selection any more.
 *
 * @param <T> the domain model type to do a Snapshot for.
 */
public interface SnapshotAdapter<T> {

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
     * @param snapshotOffer the SnapshotOffer as offered from Pekko Persistence including the db snapshot.
     * @return the transformed domain model type which is offered to the PersistentActor or {@code null}.
     */
    @Nullable
    T fromSnapshotStore(SnapshotOffer snapshotOffer);

    /**
     * Converts a "database selected snapshot" (directly loaded from the database) type to a domain model snapshot type.
     *
     * @param selectedSnapshot the SelectedSnapshot as offered from Pekko Persistence including the db snapshot.
     * @return the transformed domain model type which is offered to the PersistentActor or {@code null}.
     */
    @Nullable
    T fromSnapshotStore(SelectedSnapshot selectedSnapshot);

}
