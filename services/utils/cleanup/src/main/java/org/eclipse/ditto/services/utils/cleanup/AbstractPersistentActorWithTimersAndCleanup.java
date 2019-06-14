/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cleanup;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.cleanup.Cleanup;
import org.eclipse.ditto.signals.commands.cleanup.CleanupResponse;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.persistence.AbstractPersistentActorWithTimers;
import akka.persistence.DeleteMessagesFailure;
import akka.persistence.DeleteMessagesSuccess;
import akka.persistence.DeleteSnapshotsFailure;
import akka.persistence.DeleteSnapshotsSuccess;
import akka.persistence.JournalProtocol;
import akka.persistence.Protocol;
import akka.persistence.SnapshotProtocol;
import akka.persistence.SnapshotSelectionCriteria;

/**
 * Extends {@code AbstractPersistentActorWithTimers} to provide functionality to handle the {@link Cleanup} command by
 * deleting all persisted snapshots and events up to the latest snapshot sequence number.
 */
public abstract class AbstractPersistentActorWithTimersAndCleanup extends AbstractPersistentActorWithTimers {

    protected final DiagnosticLoggingAdapter log;

    @Nullable private ActorRef origin = null;
    @Nullable private SnapshotProtocol.Response deleteSnapshotsResponse = null;
    @Nullable private JournalProtocol.Response deleteMessagesResponse = null;
    private long lastCleanupExecutedAtSequenceNumber = 0;

    protected AbstractPersistentActorWithTimersAndCleanup() {
        this.log = LogUtil.obtain(this);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Cleanup.class, this::handleCleanupCommand)
                .match(DeleteSnapshotsSuccess.class, this::handleDeleteSnapshotsResponse)
                .match(DeleteSnapshotsFailure.class, this::handleDeleteSnapshotsResponse)
                .match(DeleteMessagesSuccess.class, this::handleDeleteMessagesResponse)
                .match(DeleteMessagesFailure.class, this::handleDeleteMessagesResponse)
                .build();
    }

    private void handleCleanupCommand(final Cleanup cleanup) {
        log.debug("Received Cleanup command: {}", cleanup);
        if (origin == null) {
            // check if there is at least one snapshot to delete
            final long latestSnapshotSequenceNumber = getLatestSnapshotSequenceNumber();
            if (latestSnapshotSequenceNumber > lastCleanupExecutedAtSequenceNumber) {
                startCleanup(latestSnapshotSequenceNumber);
            } else {
                log.debug("Snapshot revision did not change since last cleanup, nothing to delete.");
                getSender().tell(CleanupResponse.success(persistenceId(), DittoHeaders.empty()), getSelf());
            }
        } else {
            log.info("Another cleanup is already running, rejecting the new cleanup request.");
            origin.tell(CleanupResponse.failure(persistenceId(), DittoHeaders.empty()), getSelf());
        }
    }

    private void handleDeleteSnapshotsResponse(final SnapshotProtocol.Response deleteSnapshotsResponse) {
        log.debug("Received response for DeleteSnapshots command: {}",
                deleteSnapshotsResponse.getClass().getSimpleName());
        this.deleteSnapshotsResponse = deleteSnapshotsResponse;
        checkCleanupCompleted();
    }

    private void handleDeleteMessagesResponse(final JournalProtocol.Response deleteMessagesResponse) {
        log.debug("Received response for DeleteMessages command: {}",
                deleteMessagesResponse.getClass().getSimpleName());
        this.deleteMessagesResponse = deleteMessagesResponse;
        checkCleanupCompleted();
    }

    private void checkCleanupCompleted() {
        if (deleteSnapshotsResponse != null && deleteMessagesResponse != null) {
            if (isCleanupCompletedSuccessfully()) {
                log.info("Cleanup for '{}' completed.", persistenceId());
                Optional.ofNullable(origin).ifPresent(o -> o.tell(CleanupResponse.success(persistenceId(),
                        DittoHeaders.empty()), getSelf()));
            } else {
                log.info("Cleanup for '{}' failed. Snapshots: {}. Messages: {}.", persistenceId(),
                        getResponseStatus(deleteSnapshotsResponse), getResponseStatus(deleteMessagesResponse));
                Optional.ofNullable(origin).ifPresent(o -> o.tell(CleanupResponse.failure(persistenceId(),
                        DittoHeaders.empty()), getSelf()));
            }
            finishCleanup();
        }
    }

    private boolean isCleanupCompletedSuccessfully() {
        return deleteSnapshotsResponse instanceof DeleteSnapshotsSuccess &&
                deleteMessagesResponse instanceof DeleteMessagesSuccess;
    }

    private String getResponseStatus(@Nullable final Protocol.Message message) {
        if (message == null) {
            return "no response received";
        } else if (message instanceof DeleteSnapshotsFailure) {
            return String.format("%s (%s)", message.getClass().getSimpleName(),
                    ((DeleteSnapshotsFailure) message).cause().getMessage());
        } else if (message instanceof DeleteMessagesFailure) {
            return String.format("%s (%s)", message.getClass().getSimpleName(),
                    ((DeleteMessagesFailure) message).cause().getMessage());
        } else {
            return message.getClass().getSimpleName();
        }
    }

    private void startCleanup(final long latestSnapshotSequenceNumber) {
        origin = getSender();
        final long maxSeqNoToDelete = latestSnapshotSequenceNumber - 1;
        log.info("Starting cleanup for '{}', deleting snapshots and events up to sequence number {}.",
                persistenceId(), maxSeqNoToDelete);
        final SnapshotSelectionCriteria deletionCriteria =
                SnapshotSelectionCriteria.create(maxSeqNoToDelete, Long.MAX_VALUE);
        deleteMessages(maxSeqNoToDelete);
        deleteSnapshots(deletionCriteria);
        lastCleanupExecutedAtSequenceNumber = latestSnapshotSequenceNumber;
    }

    private void finishCleanup() {
        origin = null;
        deleteSnapshotsResponse = null;
        deleteMessagesResponse = null;
    }

    /**
     * @return the latest and greatest sequence number of a snapshot that was confirmed to be persisted
     */
    protected abstract long getLatestSnapshotSequenceNumber();

}
