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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

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
    private final List<AcknowledgementRequest> requestedAcksLabels;
    @Nullable private final DittoDuration requestedAcksTimeout;
    @Nullable private final DittoDuration randomizationInterval;

    ImmutableSubjectAnnouncement(@Nullable final DittoDuration beforeExpiry,
            final boolean whenDeleted,
            final List<AcknowledgementRequest> requestedAcksLabels,
            @Nullable final DittoDuration requestedAcksTimeout,
            @Nullable final DittoDuration randomizationInterval) {

        this.beforeExpiry = beforeExpiry;
        this.whenDeleted = whenDeleted;
        this.requestedAcksLabels = Collections.unmodifiableList(new ArrayList<>(requestedAcksLabels));
        this.requestedAcksTimeout = requestedAcksTimeout;
        this.randomizationInterval = randomizationInterval;
    }

    static ImmutableSubjectAnnouncement fromJson(final JsonObject jsonObject) {
        final Optional<String> beforeExpiryString = jsonObject.getValue(JsonFields.BEFORE_EXPIRY);
        final boolean whenDeleted = jsonObject.getValue(JsonFields.WHEN_DELETED).orElse(false);
        final List<AcknowledgementRequest> requestedAcksLabels =
                jsonObject.getValue(JsonFields.REQUESTED_ACKS_LABELS)
                        .map(ImmutableSubjectAnnouncement::deserializeRequestedAcks)
                        .orElse(Collections.emptyList());
        final DittoDuration requestedAcksTimeout = jsonObject.getValue(JsonFields.REQUESTED_ACKS_TIMEOUT)
                .map(ImmutableSubjectAnnouncement::parseDittoDurationOrThrow)
                .orElse(null);
        final DittoDuration randomizationInterval = jsonObject.getValue(JsonFields.RANDOMIZATION_INTERVAL)
                .map(ImmutableSubjectAnnouncement::parseDittoDurationOrThrow)
                .orElse(null);
        if (beforeExpiryString.isPresent()) {
            final DittoDuration beforeExpiry = parseDittoDurationOrThrow(beforeExpiryString.get());
            if (!BEFORE_EXPIRY_DURATION_UNITS.contains(beforeExpiry.getChronoUnit())) {
                throw SubjectAnnouncementInvalidException.newBuilder(beforeExpiryString.get()).build();
            }
            return new ImmutableSubjectAnnouncement(beforeExpiry, whenDeleted, requestedAcksLabels,
                    requestedAcksTimeout, randomizationInterval);
        } else {
            return new ImmutableSubjectAnnouncement(null, whenDeleted, requestedAcksLabels, requestedAcksTimeout,
                    randomizationInterval);
        }
    }

    private static DittoDuration parseDittoDurationOrThrow(final String duration) {
        try {
            return DittoDuration.parseDuration(duration);
        } catch (final IllegalArgumentException e) {
            throw SubjectAnnouncementInvalidException.newBuilder(duration).build();
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
    public List<AcknowledgementRequest> getRequestedAcksLabels() {
        return requestedAcksLabels;
    }

    @Override
    public Optional<DittoDuration> getRequestedAcksTimeout() {
        return Optional.ofNullable(requestedAcksTimeout);
    }

    @Override
    public Optional<DittoDuration> getRandomizationInterval() {
        return Optional.ofNullable(randomizationInterval);
    }

    @Override
    public SubjectAnnouncement setBeforeExpiry(@Nullable final DittoDuration beforeExpiry) {
        return new ImmutableSubjectAnnouncement(beforeExpiry, whenDeleted, requestedAcksLabels,
                requestedAcksTimeout, randomizationInterval);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        if (beforeExpiry != null) {
            builder.set(JsonFields.BEFORE_EXPIRY, beforeExpiry.toString());
        }
        builder.set(JsonFields.WHEN_DELETED, whenDeleted);
        if (!requestedAcksLabels.isEmpty()) {
            builder.set(JsonFields.REQUESTED_ACKS_LABELS, serializeRequestedAcks(requestedAcksLabels));
        }
        if (requestedAcksTimeout != null) {
            builder.set(JsonFields.REQUESTED_ACKS_TIMEOUT, requestedAcksTimeout.toString());
        }
        if (randomizationInterval != null) {
            builder.set(JsonFields.RANDOMIZATION_INTERVAL, randomizationInterval.toString());
        }
        return builder.build();
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof ImmutableSubjectAnnouncement) {
            final ImmutableSubjectAnnouncement that = (ImmutableSubjectAnnouncement) other;
            return Objects.equals(beforeExpiry, that.beforeExpiry) && whenDeleted == that.whenDeleted &&
                    Objects.equals(requestedAcksLabels, that.requestedAcksLabels) &&
                    Objects.equals(requestedAcksTimeout, that.requestedAcksTimeout) &&
                    Objects.equals(randomizationInterval, that.randomizationInterval);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(beforeExpiry, whenDeleted, requestedAcksLabels, requestedAcksTimeout,
                randomizationInterval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[beforeExpiry=" + beforeExpiry +
                ", whenDeleted=" + whenDeleted +
                ", requestedAcksLabels=" + requestedAcksLabels +
                ", requestedAcksTimeout=" + requestedAcksTimeout +
                ", randomizationInterval=" + randomizationInterval +
                "]";
    }

    private static List<AcknowledgementRequest> deserializeRequestedAcks(final JsonArray jsonArray) {
        return jsonArray.stream()
                .filter(JsonValue::isString)
                .map(value -> AcknowledgementRequest.parseAcknowledgementRequest(value.asString()))
                .collect(Collectors.toList());
    }

    private static JsonArray serializeRequestedAcks(final List<AcknowledgementRequest> requestedAcks) {
        return requestedAcks.stream()
                .map(AcknowledgementRequest::getLabel)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }
}
