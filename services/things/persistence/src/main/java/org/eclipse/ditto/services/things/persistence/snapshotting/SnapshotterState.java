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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.things.persistence.serializer.SnapshotTag;

import akka.actor.ActorRef;

/**
 * State of each take-snapshot request session. Contains all information necessary to deliver a response when the
 * snapshot is saved, an error is encountered, or the snapshot store timed out.
 */
final class SnapshotterState {

    private static final long INVALID_SEQUENCE_NR = -1L;

    private final boolean inProgress;
    private final long sequenceNr;
    private final SnapshotTag snapshotTag;
    private final ActorRef sender;
    private final DittoHeaders dittoHeaders;

    /**
     * Creates an uninitialized snapshotter state.
     */
    SnapshotterState() {
        this(false, INVALID_SEQUENCE_NR, SnapshotTag.UNPROTECTED, null, null);
    }

    /**
     * Creates a fully specified snapshotter state.
     *
     * @param inProgress Whether we have initiated snapshotting and are waiting for a response from the snapshot store.
     * @param sequenceNr The sequence number of the latest ongoing snapshot or the last saved snapshot.
     * @param snapshotTag Whether the snapshot is protected or unprotected.
     * @param sender Who to reply to once snapshotting finishes.
     * @param dittoHeaders Command headers of the response.
     */
    SnapshotterState(final boolean inProgress, final long sequenceNr, final SnapshotTag snapshotTag,
            @Nullable final ActorRef sender, @Nullable final DittoHeaders dittoHeaders) {
        this.inProgress = inProgress;
        this.sequenceNr = sequenceNr;
        this.snapshotTag = snapshotTag;
        this.sender = sender;
        this.dittoHeaders = dittoHeaders;
    }

    /**
     * @return Whether we have initiated snapshotting and are waiting for a response from the snapshot store.
     */
    boolean isInProgress() {
        return inProgress;
    }

    /**
     * @return The sequence number of the latest ongoing snapshot or the last saved snapshot.
     */
    long getSequenceNr() {
        return sequenceNr;
    }

    /**
     * @return Whether the snapshot is protected or unprotected.
     */
    SnapshotTag getSnapshotTag() {
        return snapshotTag;
    }

    /**
     * @return Whether the snapshot is protected from deletion.
     */
    boolean isProtected() {
        return snapshotTag == SnapshotTag.PROTECTED;
    }

    /**
     * @return Who to reply to once snapshotting finishes.
     */
    @Nullable
    ActorRef getSender() {
        return sender;
    }

    /**
     * @return Command headers of the response.
     */
    @Nullable
    DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public String toString() {
        return "seqNr=" + sequenceNr +
                " tag=" + snapshotTag +
                " inProcess=" + inProgress +
                " sender=" + sender +
                " dittoHeaders=" + dittoHeaders;
    }
}
