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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link SubjectExpiry}.
 *
 * @since 2.0.0
 */
@Immutable
final class ImmutableSubjectExpiry implements SubjectExpiry {

    private final Instant timestamp;

    private ImmutableSubjectExpiry(final Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns a new SubjectExpiry based on the provided CharSequence interpreted as ISO-8601 timestamp.
     *
     * @param expiry the expiration timestamp as ISO-8601 formatted CharSequence.
     * @return a new SubjectExpiry.
     * @throws NullPointerException if {@code expiry} is {@code null}.
     * @throws SubjectExpiryInvalidException if the provided {@code expiry} could not be parsed.
     */
    public static SubjectExpiry of(final CharSequence expiry) {
        if (expiry instanceof SubjectExpiry) {
            return (SubjectExpiry) expiry;
        }

        final Instant timestamp;
        try {
            timestamp = Instant.parse(checkNotNull(expiry, "expiry"));
        } catch (final DateTimeParseException e) {
            throw SubjectExpiryInvalidException.newBuilder(expiry)
                    .cause(e)
                    .build();
        }
        return new ImmutableSubjectExpiry(timestamp);
    }

    /**
     * Returns a new SubjectExpiry based on the provided CharSequence interpreted as ISO-8601 timestamp.
     *
     * @param expiry the character sequence forming the SubjectExpiry's value.
     * @return a new SubjectExpiry.
     * @throws NullPointerException if {@code expiry} is {@code null}.
     */
    public static SubjectExpiry of(final Instant expiry) {
        return new ImmutableSubjectExpiry(checkNotNull(expiry, "expiry"));
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean isExpired() {
        return timestamp.isBefore(Instant.now());
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSubjectExpiry that = (ImmutableSubjectExpiry) o;
        return Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }

    @Override
    @Nonnull
    public String toString() {
        return timestamp.toString();
    }
}
