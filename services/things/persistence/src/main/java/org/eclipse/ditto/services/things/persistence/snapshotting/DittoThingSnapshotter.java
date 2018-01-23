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
import org.eclipse.ditto.services.things.persistence.serializer.ThingMongoSnapshotAdapter;
import org.eclipse.ditto.services.things.persistence.serializer.ThingWithSnapshotTag;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import scala.concurrent.duration.FiniteDuration;

/**
 * Snapshotting behavior with the sudo-command {@code TakeSnapshot} as external trigger.
 */
public final class DittoThingSnapshotter extends ThingSnapshotter<TakeSnapshot, TakeSnapshotResponse> {

    private static final ThingMongoSnapshotAdapter SNAPSHOT_ADAPTER = new ThingMongoSnapshotAdapter();

    // internal constructor for unit tests.
    DittoThingSnapshotter(final ThingPersistenceActorInterface persistentActor,
            final SnapshotAdapter<ThingWithSnapshotTag> snapshotAdapter,
            final boolean snapshotDeleteOld,
            final boolean eventsDeleteOld,
            final DiagnosticLoggingAdapter log,
            final FiniteDuration snapshotInterval,
            final FiniteDuration saveSnapshotTimeout,
            final FiniteDuration loadSnapshotTimeout,
            final ActorRef snapshotPlugin) {
        super(persistentActor, snapshotAdapter, snapshotDeleteOld, eventsDeleteOld, log, snapshotInterval,
                saveSnapshotTimeout, loadSnapshotTimeout, snapshotPlugin);
    }

    private DittoThingSnapshotter(final ThingPersistenceActor thingPersistenceActor,
            final SnapshotAdapter<ThingWithSnapshotTag> snapshotAdapter,
            final boolean snapshotDeleteOld,
            final boolean eventsDeleteOld,
            @Nullable final DiagnosticLoggingAdapter log,
            @Nullable final Duration snapshotInterval) {
        super(thingPersistenceActor, snapshotAdapter, snapshotDeleteOld, eventsDeleteOld, log, snapshotInterval);
    }

    /**
     * Creates a {@code ThingSnapshotter} for a {@code ThingPersistenceActor}.
     *
     * @param thingPersistenceActor The actor in which this snapshotter is run. Must not be null.
     * @param pubSubMediator the akka distributed pubsub mediator.
     * @param snapshotDeleteOld Whether old and unprotected snapshots are to be deleted.
     * @param eventsDeleteOld Whether events before a saved snapshot are to be deleted.
     * @param log The actor's logger. If null, nothing is logged.
     * @param snapshotInterval How long to wait between scheduled maintenance snapshots.
     */
    public static DittoThingSnapshotter getInstance(final ThingPersistenceActor thingPersistenceActor,
            @SuppressWarnings({"unused", "squid:S1172"}) final ActorRef pubSubMediator,
            final boolean snapshotDeleteOld,
            final boolean eventsDeleteOld,
            @Nullable final DiagnosticLoggingAdapter log,
            @Nullable final java.time.Duration snapshotInterval) {
        return new DittoThingSnapshotter(thingPersistenceActor, SNAPSHOT_ADAPTER,
                snapshotDeleteOld, eventsDeleteOld, log, snapshotInterval);
    }

    @Override
    protected Class<TakeSnapshot> getExternalCommandClass() {
        return TakeSnapshot.class;
    }

    @Override
    protected TakeSnapshotResponse createExternalCommandResponse(final long newRevision,
            @Nullable final DittoHeaders dittoHeaders) {
        return TakeSnapshotResponse.of(newRevision, dittoHeaders);
    }

}
