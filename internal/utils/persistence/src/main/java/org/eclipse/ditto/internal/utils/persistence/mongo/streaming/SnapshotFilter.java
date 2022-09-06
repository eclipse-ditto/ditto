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

import java.util.Optional;

import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;

import akka.contrib.persistence.mongodb.SnapshottingFieldNames$;

/**
 * A record that hold optional filters for retrieving snapshots from persistence.
 */
public record SnapshotFilter(String lowerBoundPid, String pidFilter) {

    /**
     * Document field of PID in snapshot stores.
     */
    private static final String S_PROCESSOR_ID = SnapshottingFieldNames$.MODULE$.PROCESSOR_ID();

    static SnapshotFilter of(final String lowerBoundPid) {
        return new SnapshotFilter(lowerBoundPid, "");
    }

    /**
     * @param lowerBoundPid the lower-bound pid from which to start reading the snapshots
     * @param pidFilter the regex applied to the pid to filter the snapshots
     * @return new instance of SnapshotFilter
     */
    public static SnapshotFilter of(final String lowerBoundPid, final String pidFilter) {
        return new SnapshotFilter(lowerBoundPid, pidFilter);
    }

    /**
     * @return the lower-bound pid
     */
    String getLowerBoundPid() {
        return lowerBoundPid;
    }

    /**
     * @param newLowerBoundPid the new lower-bound pid replacing the current one
     * @return a new instance of SnapshotFilter with the new lower-bound pid set
     */
    SnapshotFilter withLowerBound(final String newLowerBoundPid) {
        return new SnapshotFilter(newLowerBoundPid, pidFilter);
    }

    /**
     * @return a Bson filter that can be used in a mongo query to filter the snapshots or an empty Optional if no filter was set
     */
    Optional<Bson> toMongoFilter() {
        if (!lowerBoundPid.isEmpty() && !pidFilter.isEmpty()) {
            return Optional.of(Filters.and(getLowerBoundFilter(), getNamespacesFilter()));
        } else if (!lowerBoundPid.isEmpty()) {
            return Optional.of(getLowerBoundFilter());
        } else if (!pidFilter.isEmpty()) {
            return Optional.of(getNamespacesFilter());
        } else {
            return Optional.empty();
        }
    }

    private Bson getLowerBoundFilter() {
        return Filters.gt(S_PROCESSOR_ID, lowerBoundPid);
    }

    private Bson getNamespacesFilter() {
        return Filters.regex(S_PROCESSOR_ID, pidFilter);
    }

}
