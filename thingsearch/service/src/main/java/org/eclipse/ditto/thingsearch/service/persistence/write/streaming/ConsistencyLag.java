/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.Optional;

import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;

/**
 * Timer segment names of the search updater consistency lag timer and helper methods to stop and start the segments.
 * Timer segments are named so that their chronological order coincides with their names' alphabetic order.
 * The first segment should start immediately after the timer starts.
 * The last segment stops when the timer stops.
 */
public final class ConsistencyLag {

    /**
     * Name of the search updater consistency lag timer.
     */
    public static final String TIMER_NAME = "things_search_updater_consistency_lag";

    /**
     * Tag for differentiating between search updates requiring acks and those who not require them.
     */
    public static final String TAG_SHOULD_ACK = "should_ack";

    /**
     * Name of the segment spent before leaving search updater
     */
    public static final String S0_IN_UPDATER = "s0_in_updater";

    /**
     * Name of the segment in change queue.
     */
    public static final String S1_IN_CHANGE_QUEUE = "s1_in_change_queue";

    /**
     * Name of the segment waiting for demand after leaving the change queue actor before downstream demand.
     */
    public static final String S2_WAIT_FOR_DEMAND = "s2_wait_for_demand";

    /**
     * Name of the segment spent waiting for retrieving the thing.
     */
    public static final String S3_RETRIEVE_THING = "s3_retrieve_things";

    /**
     * Name of the segment spent retrieving the enforcer for the thing.
     */
    public static final String S4_GET_ENFORCER = "s4_get_enforcer";

    /**
     * Name of the segment spent waiting for MongoDB.
     */
    public static final String S5_MONGO_BULK_WRITE = "s5_mongo_bulk_write";

    /**
     * Name of the segment spent processing acknowledgement from MongoDB.
     */
    public static final String S6_ACKNOWLEDGE = "s6_acknowledge";

    private ConsistencyLag() {
        throw new AssertionError();
    }

    /**
     * Start the segment for time spent in a thing updater.
     *
     * @param timer the timer.
     */
    public static void startS0InUpdater(final StartedTimer timer) {
        timer.startNewSegment(S0_IN_UPDATER);
    }

    /**
     * Start the segment for time spent in change queue.
     *
     * @param metadata the metadata.
     */
    public static void startS1InChangeQueue(final Metadata metadata) {
        stopAndStartSegments(metadata, S0_IN_UPDATER, S1_IN_CHANGE_QUEUE);
    }

    /**
     * Start the segment for time spent after leaving the change queue and before stream element processing.
     *
     * @param metadata the metadata.
     */
    public static void startS2WaitForDemand(final Metadata metadata) {
        stopAndStartSegments(metadata, S1_IN_CHANGE_QUEUE, S2_WAIT_FOR_DEMAND);
    }

    /**
     * Start the segment for time spent retrieving the thing.
     *
     * @param metadata the metadata.
     */
    public static void startS3RetrieveThing(final Metadata metadata) {
        stopAndStartSegments(metadata, S2_WAIT_FOR_DEMAND, S3_RETRIEVE_THING);
    }

    /**
     * Start the segment for time spent retrieving the enforcer.
     *
     * @param metadata the metadata.
     */
    public static void startS4GetEnforcer(final Metadata metadata) {
        stopAndStartSegments(metadata, S3_RETRIEVE_THING, S4_GET_ENFORCER);
    }

    /**
     * Start the segment for time spent waiting for MongoDB.
     *
     * @param metadata the metadata.
     */
    public static void startS5MongoBulkWrite(final Metadata metadata) {
        stopAndStartSegments(metadata, S4_GET_ENFORCER, S5_MONGO_BULK_WRITE);
    }

    /**
     * Start the segment for time spent waiting for acknowledgement.
     *
     * @param metadata the metadata.
     */
    public static void startS6Acknowledge(final Metadata metadata) {
        stopAndStartSegments(metadata, S5_MONGO_BULK_WRITE, S6_ACKNOWLEDGE);
    }

    private static void stopAndStartSegments(final Metadata metadata, final String segmentToStop,
            final String segmentToStart) {
        metadata.getTimers().forEach(timer -> {
            stopSegmentIfPresent(timer, segmentToStop);
            timer.startNewSegment(segmentToStart)
                    .tag(TAG_SHOULD_ACK, metadata.isShouldAcknowledge());
        });
    }

    private static void stopSegmentIfPresent(final StartedTimer timer, final String segmentName) {
        Optional.ofNullable(timer.getSegments().get(segmentName)).ifPresent(segment -> {
            if (segment.isRunning()) {
                segment.stop();
            }
        });
    }
}
