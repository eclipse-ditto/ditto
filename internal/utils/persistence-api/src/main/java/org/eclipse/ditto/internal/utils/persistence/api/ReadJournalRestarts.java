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
 * The back-off with which the read journals restart a failed query of a long pid stream, and the number of restarts
 * they derive from a stream's {@code maxIdleTime} ("how long the stream is allowed to idle without sending any
 * element"): one place for the values all {@link DittoReadJournal} implementations use.
 *
 * @since 4.0.0
 */
public final class ReadJournalRestarts {

    /** Back-off before the first restart. */
    public static final Duration MIN_BACKOFF = Duration.ofSeconds(1L);

    /** The exponential back-off is capped at this duration. */
    public static final Duration MAX_BACKOFF = Duration.ofSeconds(128L);

    /** Up to this fraction of random delay is added to every back-off. */
    public static final double RANDOM_FACTOR = 0.1;

    private ReadJournalRestarts() {
        throw new AssertionError();
    }

    /**
     * The number of restarts for a {@code maxIdleTime}: the binary logarithm of its seconds while it does not exceed
     * {@link #MAX_BACKOFF}, so that the back-offs (1 s, 2 s, 4 s, …) add up to about {@code maxIdleTime}; beyond, at
     * least 7 (1 + 2 + … + 64 = 127 s) plus one per {@link #MAX_BACKOFF}.
     *
     * @param maxIdleTime how long a stream may go without delivering an element.
     * @return the number of restarts, {@code 0} for a {@code maxIdleTime} below 2 seconds.
     */
    public static int computeMaxRestarts(final Duration maxIdleTime) {
        if (MAX_BACKOFF.minus(maxIdleTime).isNegative()) {
            // MAX_BACKOFF < maxIdleTime: back off at least 7 times (1+2+4+8+16+32+64 = 127 s)
            return Math.max(7, 6 + (int) (maxIdleTime.toMillis() / MAX_BACKOFF.toMillis()));
        } else {
            // MAX_BACKOFF >= maxIdleTime: the binary logarithm of maxIdleTime in seconds
            final int log2MaxIdleSeconds = 63 - Long.numberOfLeadingZeros(maxIdleTime.getSeconds());
            return Math.max(0, log2MaxIdleSeconds);
        }
    }

}
