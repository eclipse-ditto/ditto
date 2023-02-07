/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.internal.utils.persistence.mongo.streaming;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Filters;

import akka.contrib.persistence.mongodb.SnapshottingFieldNames$;

/**
 * A record that hold optional filters for retrieving snapshots from persistence.
 *
 * @param lowerBoundPid the lower-bound pid from which to start reading the snapshots
 * @param pidFilter the regex applied to the pid to filter the snapshots
 * @param minAgeFromNow the minimum age (based on {@code Instant.now()}) the snapshot must have in order to get
 * selected
 */
public record SnapshotFilter(String lowerBoundPid, String pidFilter, Duration minAgeFromNow) {

    /**
     * Document field of PID in snapshot stores.
     */
    private static final String S_PROCESSOR_ID = SnapshottingFieldNames$.MODULE$.PROCESSOR_ID();

    /**
     * @param lowerBoundPid the lower-bound pid from which to start reading the snapshots
     * @param minAgeFromNow the minimum age (based on {@code Instant.now()}) the snapshot must have in order to get
     * selected.
     * @return new instance of SnapshotFilter
     */
    static SnapshotFilter of(final String lowerBoundPid, final Duration minAgeFromNow) {
        return of(lowerBoundPid, "", minAgeFromNow);
    }

    /**
     * @param lowerBoundPid the lower-bound pid from which to start reading the snapshots
     * @param pidFilter the regex applied to the pid to filter the snapshots
     * @return new instance of SnapshotFilter
     */
    public static SnapshotFilter of(final String lowerBoundPid, final String pidFilter) {
        return of(lowerBoundPid, pidFilter, Duration.ZERO);
    }

    /**
     * @param lowerBoundPid the lower-bound pid from which to start reading the snapshots
     * @param pidFilter the regex applied to the pid to filter the snapshots
     * @param minAgeFromNow the minimum age (based on {@code Instant.now()}) the snapshot must have in order to get
     * selected.
     * @return new instance of SnapshotFilter
     */
    public static SnapshotFilter of(final String lowerBoundPid, final String pidFilter,
            final Duration minAgeFromNow) {
        return new SnapshotFilter(lowerBoundPid, pidFilter, minAgeFromNow);
    }

    /**
     * @param newLowerBoundPid the new lower-bound pid replacing the current one
     * @return a new instance of SnapshotFilter with the new lower-bound pid set
     */
    SnapshotFilter withLowerBound(final String newLowerBoundPid) {
        return new SnapshotFilter(newLowerBoundPid, pidFilter, minAgeFromNow);
    }

    /**
     * @return a Bson filter that can be used in a mongo query to filter the snapshots or an empty Optional if no filter was set
     */
    Bson toMongoFilter() {
        final Bson filter;
        if (!lowerBoundPid.isEmpty() && !pidFilter.isEmpty()) {
            filter = Filters.and(getLowerBoundFilter(), getNamespacesFilter());
        } else if (!lowerBoundPid.isEmpty()) {
            filter = getLowerBoundFilter();
        } else if (!pidFilter.isEmpty()) {
            filter = getNamespacesFilter();
        } else {
            filter = Filters.empty();
        }

        if (minAgeFromNow.isZero()) {
            return filter;
        } else {
            final Date nowMinusMinAgeFromNow = Date.from(Instant.now().minus(minAgeFromNow));
            final Bson eventRetentionFilter = Filters.lt("_id",
                    ObjectId.getSmallestWithDate(nowMinusMinAgeFromNow)
            );
            return Filters.and(filter, eventRetentionFilter);
        }
    }

    private Bson getLowerBoundFilter() {
        return Filters.gt(S_PROCESSOR_ID, lowerBoundPid);
    }

    private Bson getNamespacesFilter() {
        return Filters.regex(S_PROCESSOR_ID, pidFilter);
    }

}
