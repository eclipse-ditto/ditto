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
package org.eclipse.ditto.services.utils.akka.streaming;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Settings for stream consumption.
 */
@Immutable
public final class StreamConsumerSettings {

    private final Duration startOffset;
    private final Duration streamInterval;
    private final Duration initialStartOffset;
    private final Duration maxIdleTime;
    private final Duration streamingActorTimeout;
    private final int elementsStreamedPerBatch;
    private final Duration outdatedWarningOffset;

    private StreamConsumerSettings(final Duration startOffset,
            final Duration streamInterval, final Duration initialStartOffset,
            final Duration maxIdleTime, final Duration streamingActorTimeout,
            final int elementsStreamedPerBatch, final Duration outdatedWarningOffset) {
        this.startOffset = requireNonNull(checkNonNegative(startOffset));
        this.streamInterval = requireNonNull(checkNonNegative(streamInterval));
        this.initialStartOffset = requireNonNull(checkNonNegative(initialStartOffset));
        this.maxIdleTime = requireNonNull(checkNonNegative(maxIdleTime));
        this.streamingActorTimeout = requireNonNull(streamingActorTimeout);
        if (elementsStreamedPerBatch <= 0) {
            throw new IllegalArgumentException("elementsStreamedPerBatch should be positive, but is: " +
                    elementsStreamedPerBatch);
        }
        this.elementsStreamedPerBatch = elementsStreamedPerBatch;
        this.outdatedWarningOffset = requireNonNull(checkNonNegative(outdatedWarningOffset));
    }

    private static Duration checkNonNegative(final Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Negative duration: " + duration);
        }
        return duration;
    }

    /**
     * Creates settings based on the given params.
     *
     * @param startOffset the offset for the start timestamp - it is needed to make sure that we don't lose events,
     * cause the timestamp of a thing-event is created before the actual insert to the DB.
     * @param streamInterval this interval defines the minimum and maximum creation time of the entities to be queried
     * by the underlying stream.
     * @param initialStartOffset the duration starting from which the modified tags are requested for the first time
     * (further syncs will know the last-success timestamp).
     * @param maxIdleTime the maximum idle time of a stream forwarder. A stream is considered idle when it does not
     * retrieve any messages.
     * @param streamingActorTimeout timeout at the streaming actor (server) side.
     * @param elementsStreamedPerBatch the elements to be streamed per batch.
     * @param outdatedWarningOffset if a query-start is more than this offset in the past, a warning will be logged.
     * @return the created settings.
     */
    public static StreamConsumerSettings of(final Duration startOffset,
            final Duration streamInterval, final Duration initialStartOffset,
            final Duration maxIdleTime, final Duration streamingActorTimeout,
            final int elementsStreamedPerBatch, final Duration outdatedWarningOffset) {
        return new StreamConsumerSettings(startOffset, streamInterval, initialStartOffset, maxIdleTime,
                streamingActorTimeout, elementsStreamedPerBatch, outdatedWarningOffset);
    }

    /**
     * Returns the offset for the start timestamp - it is needed to make sure that we don't lose events,
     * cause the timestamp of a thing-event is created before the actual insert to the DB.
     *
     * @return the start-offset
     */
    public Duration getStartOffset() {
        return startOffset;
    }

    /**
     * Returns the interval which defines the minimum and maximum creation time of the entities to be queried
     * by the underlying stream.
     *
     * @return the stream-interval
     */
    public Duration getStreamInterval() {
        return streamInterval;
    }

    /**
     * Returns the duration starting from which the modified tags are requested for the first time
     * (further syncs will know the last-success timestamp).
     *
     * @return the initial start-offset
     */
    public Duration getInitialStartOffset() {
        return initialStartOffset;
    }

    /**
     * Returns the maximum idle time of a stream forwarder. A stream is considered idle when it does not
     * retrieve any messages.
     *
     * @return the maximum idle time
     */
    public Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    /**
     * Returns the elements to be streamed per batch.
     *
     * @return the elements to be streamed per batch.
     */
    public int getElementsStreamedPerBatch() {
        return elementsStreamedPerBatch;
    }

    /**
     * Returns timeout at the streaming actor (server) side.
     *
     * @return timeout at server side.
     */
    public Duration getStreamingActorTimeout() {
        return streamingActorTimeout;
    }

    /**
     * Returns the offset which defines how long a query-start can be in the past until a warning will be
     * logged.
     *
     * @return the offset
     */
    public Duration getOutdatedWarningOffset() {
        return outdatedWarningOffset;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StreamConsumerSettings that = (StreamConsumerSettings) o;
        return elementsStreamedPerBatch == that.elementsStreamedPerBatch &&
                Objects.equals(startOffset, that.startOffset) &&
                Objects.equals(streamInterval, that.streamInterval) &&
                Objects.equals(initialStartOffset, that.initialStartOffset) &&
                Objects.equals(maxIdleTime, that.maxIdleTime) &&
                Objects.equals(streamingActorTimeout, that.streamingActorTimeout) &&
                Objects.equals(outdatedWarningOffset, that.outdatedWarningOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startOffset, streamInterval, initialStartOffset, maxIdleTime, streamingActorTimeout,
                elementsStreamedPerBatch, outdatedWarningOffset);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "startOffset=" + startOffset +
                ", streamInterval=" + streamInterval +
                ", initialStartOffset=" + initialStartOffset +
                ", maxIdleTime=" + maxIdleTime +
                ", streamingActorTimeout" + streamingActorTimeout +
                ", elementsStreamedPerBatch=" + elementsStreamedPerBatch +
                ", outdatedWarningOffset=" + outdatedWarningOffset +
                ']';
    }
}
