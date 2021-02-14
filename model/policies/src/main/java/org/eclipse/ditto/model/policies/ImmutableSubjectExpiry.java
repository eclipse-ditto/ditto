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
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoDuration;

/**
 * An immutable implementation of {@link SubjectExpiry}.
 *
 * @since 2.0.0
 */
@Immutable
final class ImmutableSubjectExpiry implements SubjectExpiry {

    private static final Set<ChronoUnit> NOTIFY_BEFORE_DURATION_UNITS =
            EnumSet.of(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);

    private final Instant timestamp;
    @Nullable private final DittoDuration notifyBefore;

    private ImmutableSubjectExpiry(final Instant timestamp,
            @Nullable final DittoDuration notifyBefore) {
        this.timestamp = timestamp;
        this.notifyBefore = notifyBefore;
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
        } else {
            return parseAndValidate(expiry, null);
        }
    }

    static ImmutableSubjectExpiry parseAndValidate(final CharSequence expiry,
            @Nullable final CharSequence notifyBefore) {
        return new ImmutableSubjectExpiry(parseExpiryInstant(expiry), parseNotifyBeforeDuration(notifyBefore));
    }

    /**
     * Returns a new SubjectExpiry based on the provided CharSequence interpreted as ISO-8601 timestamp.
     *
     * @param expiry the character sequence forming the SubjectExpiry's value.
     * @return a new SubjectExpiry.
     * @throws NullPointerException if {@code expiry} is {@code null}.
     */
    public static SubjectExpiry of(final Instant expiry) {
        return new ImmutableSubjectExpiry(checkNotNull(expiry, "expiry"), null);
    }

    static SubjectExpiry fromJson(final JsonValue jsonValue) {
        if (jsonValue.isString()) {
            return of(jsonValue.asString());
        } else {
            final JsonObject jsonObject = jsonValue.asObject();
            final String timestamp = jsonObject.getValueOrThrow(JsonFields.TIMESTAMP);
            final String notifyBefore = jsonObject.getValue(JsonFields.NOTIFY_BEFORE).orElse(null);
            return parseAndValidate(timestamp, notifyBefore);
        }
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
    public JsonValue toJson() {
        if (notifyBefore == null) {
            return JsonValue.of(timestamp.toString());
        } else {
            return JsonObject.newBuilder()
                    .set(JsonFields.TIMESTAMP, timestamp.toString())
                    .set(JsonFields.NOTIFY_BEFORE, notifyBefore.toString())
                    .build();
        }
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
        return Objects.equals(timestamp, that.timestamp) && Objects.equals(notifyBefore, that.notifyBefore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, notifyBefore);
    }

    @Override
    @Nonnull
    public String toString() {
        return toJson().formatAsString();
    }

    private static Instant parseExpiryInstant(final CharSequence expiry) {
        try {
            return Instant.parse(checkNotNull(expiry, "expiry"));
        } catch (final DateTimeParseException e) {
            throw SubjectExpiryInvalidException.newBuilder(expiry)
                    .cause(e)
                    .build();
        }
    }

    @Nullable
    private static DittoDuration parseNotifyBeforeDuration(final @Nullable CharSequence notifyBefore) {
        if (notifyBefore == null) {
            return null;
        }
        try {
            return validateNotifyBeforeDuration(DittoDuration.parseDuration(notifyBefore));
        } catch (final NullPointerException | IllegalArgumentException e) {
            throw SubjectExpiryInvalidException.newBuilderForNotifyBefore(notifyBefore).build();
        }
    }

    private static DittoDuration validateNotifyBeforeDuration(final DittoDuration dittoDuration) {
        if (!NOTIFY_BEFORE_DURATION_UNITS.contains(dittoDuration.getChronoUnit())) {
            throw SubjectExpiryInvalidException.newBuilderForNotifyBefore(dittoDuration).build();
        }
        return dittoDuration;
    }
}
