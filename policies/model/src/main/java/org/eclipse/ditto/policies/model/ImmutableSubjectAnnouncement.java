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
package org.eclipse.ditto.policies.model;

import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.DittoDuration;

/**
 * Immutable implementation of {@link SubjectAnnouncement}.
 *
 * @since 2.0.0
 */
@Immutable
final class ImmutableSubjectAnnouncement implements SubjectAnnouncement {

    private static final Set<ChronoUnit> BEFORE_EXPIRY_DURATION_UNITS =
            EnumSet.of(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);

    @Nullable
    private final DittoDuration beforeExpiry;
    private final boolean whenDeleted;

    ImmutableSubjectAnnouncement(@Nullable final DittoDuration beforeExpiry, final boolean whenDeleted) {
        this.beforeExpiry = beforeExpiry;
        this.whenDeleted = whenDeleted;
    }

    static ImmutableSubjectAnnouncement fromJson(final JsonObject jsonObject) {
        final Optional<String> beforeExpiryString = jsonObject.getValue(JsonFields.BEFORE_EXPIRY);
        final boolean whenDeleted = jsonObject.getValue(JsonFields.WHEN_DELETED).orElse(false);
        if (beforeExpiryString.isPresent()) {
            try {
                final DittoDuration beforeExpiry = DittoDuration.parseDuration(beforeExpiryString.get());
                if (!BEFORE_EXPIRY_DURATION_UNITS.contains(beforeExpiry.getChronoUnit())) {
                    throw SubjectAnnouncementInvalidException.newBuilder(beforeExpiryString.get()).build();
                }
                return new ImmutableSubjectAnnouncement(beforeExpiry, whenDeleted);
            } catch (final IllegalArgumentException e) {
                throw SubjectAnnouncementInvalidException.newBuilder(beforeExpiryString.get()).build();
            }
        } else {
            return new ImmutableSubjectAnnouncement(null, whenDeleted);
        }
    }

    @Override
    public Optional<DittoDuration> getBeforeExpiry() {
        return Optional.ofNullable(beforeExpiry);
    }

    @Override
    public boolean isWhenDeleted() {
        return whenDeleted;
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        if (beforeExpiry != null) {
            builder.set(JsonFields.BEFORE_EXPIRY, beforeExpiry.toString());
        }
        builder.set(JsonFields.WHEN_DELETED, whenDeleted);
        return builder.build();
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof ImmutableSubjectAnnouncement) {
            final ImmutableSubjectAnnouncement that = (ImmutableSubjectAnnouncement) other;
            return Objects.equals(beforeExpiry, that.beforeExpiry) && whenDeleted == that.whenDeleted;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(beforeExpiry, whenDeleted);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[beforeExpiry=" + beforeExpiry +
                ", whenDeleted=" + whenDeleted +
                "]";
    }
}
