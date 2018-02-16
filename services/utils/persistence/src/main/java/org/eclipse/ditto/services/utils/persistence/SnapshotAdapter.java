/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.persistence;

import javax.annotation.Nullable;

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

}
