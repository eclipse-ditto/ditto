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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import static java.text.MessageFormat.format;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;

import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;

/**
 * Actor for checking if the duration since the last synchronization is acceptable low.
 */
final class LastSuccessfulStreamCheckingActor extends AbstractHealthCheckingActor {

    /**
     * Indicates whether the sync stream is enabled or not. If not the health status will always be unknown.
     */
    private final boolean syncEnabled;

    /**
     * Used to determine the time stamp of the last successful stream.
     */
    private final TimestampPersistence streamMetadataPersistence;

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

    private final ActorMaterializer materializer = ActorMaterializer.create(getContext());

    /**
     * Constructs a new LastSuccessfulStreamCheckingActor object.
     * The visibility is package-private for unit tests.
     *
     * @param syncConfig the synchronization configuration settings.
     * @param streamMetadataPersistence used to determine the time stamp of the last successful stream.
     */
    LastSuccessfulStreamCheckingActor(final SyncConfig syncConfig,
            final TimestampPersistence streamMetadataPersistence, final Instant startUpInstant) {

        syncEnabled = syncConfig.isEnabled();
        syncOutdatedWarningOffset = syncConfig.getOutdatedWarningOffset();
        syncOutdatedErrorOffset = syncConfig.getOutdatedErrorOffset();
        this.streamMetadataPersistence = streamMetadataPersistence;
        this.startUpInstant = checkNotNull(startUpInstant, "start-up instant");
    }

    /**
     * Creates Akka configuration object Props for this LastSuccessfulStreamCheckingActor.
     *
     * @param syncConfig the synchronization configuration settings.
     * @param streamMetadataPersistence used to determine the time stamp of the last successful stream.
     * @return the Akka configuration Props object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Props props(final SyncConfig syncConfig, final TimestampPersistence streamMetadataPersistence) {
        checkNotNull(syncConfig, "synchronization config");
        checkNotNull(streamMetadataPersistence, "stream metadata persistence");

        return Props.create(LastSuccessfulStreamCheckingActor.class, new Creator<LastSuccessfulStreamCheckingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public LastSuccessfulStreamCheckingActor create() {
                return new LastSuccessfulStreamCheckingActor(syncConfig, streamMetadataPersistence, Instant.now());
            }
        });
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(StatusInfo.class, this::updateHealth)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        final CompletionStage<StatusInfo> statusInfoFuture;
        if (syncEnabled) {
            statusInfoFuture = getStatusInfo();
        } else {
            statusInfoFuture = CompletableFuture.completedFuture(StatusInfo.fromStatus(StatusInfo.Status.UNKNOWN,
                    Collections.singleton(
                            StatusDetailMessage.of(StatusDetailMessage.Level.WARN, SYNC_DISABLED_MESSAGE))));
        }
        final CompletionStage<StatusInfo> statusInfoFutureWithErrorHandling =
                statusInfoFuture.handle((result, error) -> {
                    if (error == null) {
                        return result;
                    } else {
                        final String message = buildRetrievalErrorMessage(error);
                        log.error(error, message);
                        return createStatusInfo(StatusDetailMessage.Level.ERROR, message);
                    }
                });

        PatternsCS.pipe(statusInfoFutureWithErrorHandling, getContext().dispatcher()).to(getSelf());
    }

    private CompletionStage<StatusInfo> getStatusInfo() {
        return streamMetadataPersistence.getTimestampAsync()
                .map(instantOfLastSuccessfulStreamOptional -> {
                    final StatusInfo statusInfo;
                    if (instantOfLastSuccessfulStreamOptional.isPresent()) {
                        final Duration durationSinceLastSuccessfulStream =
                                calculateDurationSinceLastSuccessfulStream(instantOfLastSuccessfulStreamOptional.get());

                        if (syncErrorOffsetExceeded(durationSinceLastSuccessfulStream)) {
                            final String message =
                                    buildSyncErrorOffsetExceededErrorMessage(durationSinceLastSuccessfulStream);
                            statusInfo = createStatusInfo(StatusDetailMessage.Level.ERROR, message);
                        } else if (syncWarningOffsetExceeded(durationSinceLastSuccessfulStream)) {
                            final String message =
                                    buildSyncWarningOffsetExceededErrorMessage(durationSinceLastSuccessfulStream);
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
                })
                .runWith(Sink.head(), materializer);
    }

    private static StatusInfo createStatusInfo(final StatusDetailMessage.Level level, final String message) {
        return StatusInfo.fromDetail(StatusDetailMessage.of(level, message));
    }

    private static Duration calculateDurationSinceLastSuccessfulStream(final Instant instantOfLastSuccessfulStream) {
        return Duration.between(instantOfLastSuccessfulStream, Instant.now());
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
                syncOutdatedErrorOffset);
    }

    private String buildSyncWarningOffsetExceededErrorMessage(final Duration durationSinceLastSuccessfulStream) {
        return format("{0} Maximum duration before showing this warning is <{1}>.",
                buildInformationAboutLastSuccessfulStreamMessage(durationSinceLastSuccessfulStream),
                syncOutdatedWarningOffset);
    }

    private static String buildInformationAboutLastSuccessfulStreamMessage(
            final Duration durationSinceLastSuccessfulStream) {

        return format("End timestamp of last successful sync is about <{0}> minutes ago.",
                durationSinceLastSuccessfulStream.toMinutes());
    }

    private static String buildRetrievalErrorMessage(final Throwable reason) {
        return format("An error occurred when asking for the end timestamp of last successful sync. Reason: <{0}>.",
                reason);
    }

}
