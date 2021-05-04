/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import java.time.Instant;

import javax.annotation.concurrent.Immutable;

/**
 * Represents a {@link Subject} expiry timestamp indicating the instant when a Subject is automatically removed from a
 * Policy entry.
 *
 * @since 2.0.0
 */
@Immutable
public interface SubjectExpiry extends CharSequence, Comparable<SubjectExpiry> {

    /**
     * Returns a new {@link SubjectExpiry} with the specified {@code expiry} from a ISO-8601 formatted char sequence.
     *
     * @param expiry the expiry as ISO-8601 formatted char sequence.
     * @return the new {@link SubjectExpiry}.
     * @throws NullPointerException if {@code expiry} is {@code null}.
     * @throws SubjectExpiryInvalidException if the provided {@code expiry} could not be parsed.
     */
    static SubjectExpiry newInstance(final CharSequence expiry) {
        return PoliciesModelFactory.newSubjectExpiry(expiry);
    }

    /**
     * Returns a new {@link SubjectExpiry} with the specified {@code expiry} Instant.
     *
     * @param expiry the expiry Instant.
     * @return the new {@link SubjectExpiry}.
     * @throws NullPointerException if {@code expiry} is {@code null}.
     */
    static SubjectExpiry newInstance(final Instant expiry) {
        return PoliciesModelFactory.newSubjectExpiry(expiry);
    }

    /**
     * Returns the timestamp of the expiry as Instant.
     *
     * @return the timestamp of the expiry.
     */
    Instant getTimestamp();

    /**
     * Returns {@code true} when the configured expiry timestamp is expired (is in the past).
     *
     * @return whether the expiry is expired or not.
     */
    boolean isExpired();

    /**
     * Returns the ISO-8601 representation of this expiry timestamp.
     *
     * @return the ISO-8601 representation of this expiry timestamp.
     */
    @Override
    String toString();

    @Override
    default int compareTo(final SubjectExpiry o) {
        return getTimestamp().compareTo(o.getTimestamp());
    }
}
