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
package org.eclipse.ditto.services.things.persistence.snapshotting;

import java.time.Duration;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.things.commands.sudo.TakeSnapshot;
import org.eclipse.ditto.services.models.things.commands.sudo.TakeSnapshotResponse;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActorInterface;
import org.eclipse.ditto.services.things.persistence.serializer.things.ThingWithSnapshotTag;
import org.eclipse.ditto.services.utils.akka.persistence.SnapshotAdapter;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import scala.concurrent.duration.FiniteDuration;

/**
 * Snapshotting behavior with the sudo-command {@code TakeSnapshot} as external trigger.
 */
public final class DittoThingSnapshotter extends ThingSnapshotter<TakeSnapshot, TakeSnapshotResponse> {

    // internal constructor for unit tests.
    DittoThingSnapshotter(final ThingPersistenceActorInterface persistentActor, final DiagnosticLoggingAdapter log,
            final SnapshotAdapter<ThingWithSnapshotTag> taggedSnapshotAdapter, final ActorRef snapshotPlugin,
            final boolean snapshotDeleteOld, final boolean eventsDeleteOld,
            final FiniteDuration snapshotInterval, final FiniteDuration saveSnapshotTimeout,
            final FiniteDuration loadSnapshotTimeout) {
        super(persistentActor, log, taggedSnapshotAdapter, snapshotPlugin, snapshotDeleteOld, eventsDeleteOld,
                snapshotInterval, saveSnapshotTimeout, loadSnapshotTimeout);
    }

    private DittoThingSnapshotter(
            final ThingPersistenceActor thingPersistenceActor,
            @Nullable final DiagnosticLoggingAdapter log,
            @Nullable final Duration snapshotInterval, final boolean snapshotDeleteOld,
            final boolean eventsDeleteOld) {
        super(thingPersistenceActor, log, snapshotInterval, snapshotDeleteOld, eventsDeleteOld);
    }

    @Override
    protected Class<TakeSnapshot> getExternalCommandClass() {
        return TakeSnapshot.class;
    }

    @Override
    protected TakeSnapshotResponse createExternalCommandResponse(final long newRevision,
            final DittoHeaders dittoHeaders) {
        return TakeSnapshotResponse.of(newRevision, dittoHeaders);
    }

    /**
     * Creates a {@code ThingSnapshotter} for a {@code ThingPersistenceActor}.
     *
     * @param thingPersistenceActor The actor in which this snapshotter is run. Must not be null.
     * @param log The actor's logger. If null, nothing is logged.
     * @param snapshotDeleteOld Whether old and unprotected snapshots are to be deleted.
     * @param eventsDeleteOld Whether events before a saved snapshot are to be deleted.
     */
    public static DittoThingSnapshotter getInstance(final ThingPersistenceActor thingPersistenceActor,
            @Nullable final DiagnosticLoggingAdapter log, @Nullable final java.time.Duration snapshotInterval,
            final boolean snapshotDeleteOld, final boolean eventsDeleteOld) {
        return new DittoThingSnapshotter(thingPersistenceActor, log, snapshotInterval, snapshotDeleteOld,
                eventsDeleteOld);
    }
}
