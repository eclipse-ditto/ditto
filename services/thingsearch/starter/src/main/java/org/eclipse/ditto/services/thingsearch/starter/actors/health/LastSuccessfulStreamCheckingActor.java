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
package org.eclipse.ditto.services.thingsearch.starter.actors.health;

import static java.text.MessageFormat.format;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.Creator;

/**
 * Actor for checking if the duration since the last synchronization is acceptable low.
 */
public class LastSuccessfulStreamCheckingActor extends AbstractHealthCheckingActor {

    /**
     * Indicates whether the sync stream is enabled or not. If not the health status will always be unknown.
     */
    private final boolean syncEnabled;

    /**
     * Used to determine the time stamp of the last successful stream.
     */
    private final StreamMetadataPersistence streamMetadataPersistence;

    /**
     * Defines the maximum duration that is allowed without a successful stream. If this duration is exceeded, the
     * health check will set the status to {@link StatusInfo.Status#DOWN}.
     */
    private final Duration syncOutdatedErrorOffset;

    /**
     * Defines the maximum duration that is allowed without a
     * successful stream. If this duration is exceeded, the health check will show a warning.
     */
    private final Duration syncOutdatedWarningOffset;

    /**
     * Creation time of this actor. No ERROR state is reported a period of time after actor starts so that the stream
     * has a chance to complete.
     */
    private final Instant startUpInstant;

    static final String NO_SUCCESSFUL_STREAM_YET_MESSAGE = "No successful stream, yet.";

    static final String SYNC_DISABLED_MESSAGE = "Sync is currently disabled. No status will be retrieved";

    /**
     * Constructs a {@code HealthCheckingActor}.
     *
     * @param syncEnabled indicates whether the sync is enabled. If not the health status will always be
     * {@link org.eclipse.ditto.services.utils.health.StatusInfo.Status#UNKNOWN}.
     * @param streamMetadataPersistence Used to determine the time stamp of the last successful stream.
     * @param syncOutdatedWarningOffset Defines the maximum duration that is allowed without a
     * successful stream. If this duration is exceeded, the health check will show a warning.
     * @param syncOutdatedErrorOffset Defines the maximum duration that is allowed without a
     * successful stream. If this duration is exceeded, the health check will set the status to
     * {@link org.eclipse.ditto.services.utils.health.StatusInfo.Status#DOWN}.
     */
    private LastSuccessfulStreamCheckingActor(final boolean syncEnabled,
            final StreamMetadataPersistence streamMetadataPersistence, final Duration syncOutdatedWarningOffset,
            final Duration syncOutdatedErrorOffset,
            final Instant startUpInstant) {
        this.syncEnabled = syncEnabled;
        this.streamMetadataPersistence = requireNonNull(streamMetadataPersistence);
        this.syncOutdatedWarningOffset = requireNonNull(syncOutdatedWarningOffset);
        this.syncOutdatedErrorOffset = requireNonNull(syncOutdatedErrorOffset);
        this.startUpInstant = requireNonNull(startUpInstant);
    }

    /**
     * Package-private constructor for unit tests.
     *
     * @param streamHealthCheckConfigurationProperties Holds the configuration properties of this health check.
     * @param startUpInstant When this actor was supposed to be created.
     */
    LastSuccessfulStreamCheckingActor(
            final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties,
            final Instant startUpInstant) {

        this(streamHealthCheckConfigurationProperties.isSyncEnabled(),
                streamHealthCheckConfigurationProperties.getStreamMetadataPersistence(),
                streamHealthCheckConfigurationProperties.getWarningOffset(),
                streamHealthCheckConfigurationProperties.getErrorOffset(),
                startUpInstant);
    }

    /**
     * Creates Akka configuration object Props for this {@link LastSuccessfulStreamCheckingActor}.
     *
     * @param streamHealthCheckConfigurationProperties Holds the configuration properties of this health check.
     * @return the Akka configuration Props object.
     */
    public static Props props(
            final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties) {
        return Props.create(LastSuccessfulStreamCheckingActor.class, new Creator<LastSuccessfulStreamCheckingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public LastSuccessfulStreamCheckingActor create() {
                return new LastSuccessfulStreamCheckingActor(streamHealthCheckConfigurationProperties, Instant.now());
            }
        });
    }

    @Override
    protected Receive matchCustomMessages() {
        return AbstractActor.emptyBehavior();
    }

    @Override
    protected void triggerHealthRetrieval() {
        StatusInfo statusInfo;

        try {
            if (this.syncEnabled) {
                statusInfo = this.getStatusInfo();
            } else {
                statusInfo = createStatusInfo(StatusInfo.Status.UNKNOWN, StatusDetailMessage.Level.WARN,
                        SYNC_DISABLED_MESSAGE);
            }
        } catch (final RuntimeException e) {
            final String message = buildRetrievalErrorMessage(e);
            log.error(e, message);

            statusInfo = createStatusInfo(StatusDetailMessage.Level.ERROR, message);
        }

        updateHealth(statusInfo);
    }

    private StatusInfo getStatusInfo() {
        final Optional<Instant> instantOfLastSuccessfulStreamOptional =
                this.streamMetadataPersistence.retrieveLastSuccessfulStreamEnd();

        final StatusInfo statusInfo;

        if (instantOfLastSuccessfulStreamOptional.isPresent()) {
            final Duration durationSinceLastSuccessfulStream =
                    calculateDurationSinceLastSuccessfulStream(instantOfLastSuccessfulStreamOptional.get());

            if (syncErrorOffsetExceeded(durationSinceLastSuccessfulStream)) {
                final String message = buildSyncErrorOffsetExceededErrorMessage(durationSinceLastSuccessfulStream);
                statusInfo = createStatusInfo(StatusDetailMessage.Level.ERROR, message);
            } else if (syncWarningOffsetExceeded(durationSinceLastSuccessfulStream)) {
                final String message = buildSyncWarningOffsetExceededErrorMessage(durationSinceLastSuccessfulStream);
                statusInfo = createStatusInfo(StatusDetailMessage.Level.WARN, message);
            } else {
                final String message =
                        buildInformationAboutLastSuccessfulStreamMessage(durationSinceLastSuccessfulStream);
                statusInfo = createStatusInfo(StatusDetailMessage.Level.INFO, message);
            }
        } else {
            statusInfo = createStatusInfo(StatusDetailMessage.Level.WARN, NO_SUCCESSFUL_STREAM_YET_MESSAGE);
        }
        return statusInfo;
    }

    private StatusInfo createStatusInfo(final StatusDetailMessage.Level level, final String message) {
        return StatusInfo.fromDetail(StatusDetailMessage.of(level, message));
    }

    private StatusInfo createStatusInfo(final StatusInfo.Status status, final StatusDetailMessage.Level level,
            final String message) {
        return StatusInfo.fromStatus(status, Collections.singleton(StatusDetailMessage.of(level, message)));
    }

    private Duration calculateDurationSinceLastSuccessfulStream(final Instant instantOfLastSuccessfulStream) {
        final Instant now = Instant.now();

        return Duration.between(instantOfLastSuccessfulStream, now);
    }

    /**
     * Test if status should be ERROR. It will not return {@code true} within {@code syncOutdatedWarningOffset} of
     * actor startup to give the stream a chance to complete no matter how outdated the database is.
     *
     * @param durationSinceLastSuccessfulStream How long ago was the last successful sync from now.
     * @return whether the status should be ERROR.
     */
    private boolean syncErrorOffsetExceeded(final Duration durationSinceLastSuccessfulStream) {
        return startUpInstant.plus(syncOutdatedErrorOffset).isBefore(Instant.now()) &&
                durationSinceLastSuccessfulStream.compareTo(syncOutdatedErrorOffset) > 0;
    }

    private boolean syncWarningOffsetExceeded(final Duration durationSinceLastSuccessfulStream) {
        return durationSinceLastSuccessfulStream.compareTo(syncOutdatedWarningOffset) > 0;
    }

    private String buildSyncErrorOffsetExceededErrorMessage(final Duration durationSinceLastSuccessfulStream) {
        return format("{0} Maximum duration before showing this error is <{1}>.",
                buildInformationAboutLastSuccessfulStreamMessage(durationSinceLastSuccessfulStream),
                this.syncOutdatedErrorOffset);
    }

    private String buildSyncWarningOffsetExceededErrorMessage(final Duration durationSinceLastSuccessfulStream) {
        return format("{0} Maximum duration before showing this warning is <{1}>.",
                buildInformationAboutLastSuccessfulStreamMessage(durationSinceLastSuccessfulStream),
                this.syncOutdatedWarningOffset);
    }

    private String buildInformationAboutLastSuccessfulStreamMessage(final Duration durationSinceLastSuccessfulStream) {
        return format("End timestamp of last successful sync is about <{0}> minutes ago.",
                durationSinceLastSuccessfulStream.toMinutes());
    }

    private String buildRetrievalErrorMessage(final Throwable reason) {
        return format("An error occurred when asking for the end timestamp of last successful sync. Reason: <{0}>.",
                reason);
    }
}
