/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.api;

import java.time.Duration;

/**
 * Backend-agnostic filter for selecting snapshots when streaming from the persistence backend.
 * Each backend implementation translates these criteria into its own query language.
 *
 * @param lowerBoundPid the lower-bound persistence ID from which to start reading snapshots
 * @param pidFilter a regex applied to the persistence ID to filter the snapshots; empty string means no filter
 * @param minAgeFromNow the minimum age (based on {@code Instant.now()}) the snapshot must have in order to get
 * selected
 */
public record SnapshotFilter(String lowerBoundPid, String pidFilter, Duration minAgeFromNow) {

    public static SnapshotFilter of(final String lowerBoundPid, final Duration minAgeFromNow) {
        return new SnapshotFilter(lowerBoundPid, "", minAgeFromNow);
    }

    public static SnapshotFilter of(final String lowerBoundPid, final String pidFilter) {
        return new SnapshotFilter(lowerBoundPid, pidFilter, Duration.ZERO);
    }

    public static SnapshotFilter of(final String lowerBoundPid, final String pidFilter,
            final Duration minAgeFromNow) {
        return new SnapshotFilter(lowerBoundPid, pidFilter, minAgeFromNow);
    }

    public SnapshotFilter withLowerBound(final String newLowerBoundPid) {
        return new SnapshotFilter(newLowerBoundPid, pidFilter, minAgeFromNow);
    }

}
