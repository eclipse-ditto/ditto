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
package org.eclipse.ditto.services.utils.persistentactors;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistence;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistenceResponse;

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
 * Extends {@code AbstractPersistentActorWithTimers} to provide functionality to handle the {@link CleanupPersistence} command by
 * deleting all persisted snapshots and events up to the latest snapshot sequence number.
 */
public abstract class AbstractPersistentActorWithTimersAndCleanup extends AbstractPersistentActorWithTimers {

    private static final int STALE_EVENTS_KEPT_AFTER_CLEANUP = 0;

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
                .match(CleanupPersistence.class, this::handleCleanupCommand)
                .match(DeleteSnapshotsSuccess.class, this::handleDeleteSnapshotsResponse)
                .match(DeleteSnapshotsFailure.class, this::handleDeleteSnapshotsResponse)
                .match(DeleteMessagesSuccess.class, this::handleDeleteMessagesResponse)
                .match(DeleteMessagesFailure.class, this::handleDeleteMessagesResponse)
                .build();
    }

    /**
     * Return the number of events to keep after a cleanup action.
     * If this number is positive, the PID will not be removed from
     * the set of current PIDs known to the persistence plugin.
     *
     * @return number of stale events to keep.
     */
    protected long staleEventsKeptAfterCleanup() {
        return STALE_EVENTS_KEPT_AFTER_CLEANUP;
    }

    private void handleCleanupCommand(final CleanupPersistence cleanupPersistence) {
        log.debug("Received Cleanup command: {}", cleanupPersistence);
        if (origin == null) {
            // check if there is at least one snapshot to delete
            final long latestSnapshotSequenceNumber = getLatestSnapshotSequenceNumber();
            if (latestSnapshotSequenceNumber > lastCleanupExecutedAtSequenceNumber) {
                startCleanup(latestSnapshotSequenceNumber);
            } else {
                log.debug("Snapshot revision did not change since last cleanup, nothing to delete.");
                getSender().tell(
                        CleanupPersistenceResponse.success(DefaultEntityId.of(persistenceId()), DittoHeaders.empty()),
                        getSelf());
            }
        } else {
            log.info("Another cleanup is already running, rejecting the new cleanup request.");
            origin.tell(CleanupPersistenceResponse.failure(DefaultEntityId.of(persistenceId()), DittoHeaders.empty()),
                    getSelf());
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
                Optional.ofNullable(origin)
                        .ifPresent(o -> o.tell(CleanupPersistenceResponse.success(DefaultEntityId.of(persistenceId()),
                        DittoHeaders.empty()), getSelf()));
            } else {
                log.info("Cleanup for '{}' failed. Snapshots: {}. Messages: {}.", persistenceId(),
                        getResponseStatus(deleteSnapshotsResponse), getResponseStatus(deleteMessagesResponse));
                Optional.ofNullable(origin)
                        .ifPresent(o -> o.tell(CleanupPersistenceResponse.failure(DefaultEntityId.of(persistenceId()),
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
        final long maxSnapSeqNoToDelete = latestSnapshotSequenceNumber - 1;
        final long maxEventSeqNoToDelete = latestSnapshotSequenceNumber - staleEventsKeptAfterCleanup();
        log.info("Starting cleanup for '{}', deleting snapshots to sequence number {} and events to {}.",
                persistenceId(), maxSnapSeqNoToDelete, maxEventSeqNoToDelete);
        final SnapshotSelectionCriteria deletionCriteria =
                SnapshotSelectionCriteria.create(maxSnapSeqNoToDelete, Long.MAX_VALUE);
        deleteMessages(maxEventSeqNoToDelete);
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
