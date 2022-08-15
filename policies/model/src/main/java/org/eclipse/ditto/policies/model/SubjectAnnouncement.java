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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Represents announcement settings of a {@link Subject}.
 *
 * @since 2.0.0
 */
@Immutable
public interface SubjectAnnouncement extends Jsonifiable<JsonObject> {

    /**
     * Returns a new {@link SubjectAnnouncement} with the given configuration.
     *
     * @param beforeExpiry duration before expiry when an announcement should be sent, or null if no announcement should
     * be sent.
     * @param whenDeleted whether an announcement should be sent when the subject is deleted.
     * @return the new {@link SubjectAnnouncement}.
     */
    static SubjectAnnouncement of(@Nullable final DittoDuration beforeExpiry, final boolean whenDeleted) {
        return new ImmutableSubjectAnnouncement(beforeExpiry, whenDeleted, Collections.emptyList(),
                null, null);
    }

    /**
     * Returns a new {@link SubjectAnnouncement} with the given configuration.
     *
     * @param beforeExpiry duration before expiry when an announcement should be sent, or null if no announcement should
     * be sent.
     * @param whenDeleted whether an announcement should be sent when the subject is deleted.
     * @param requestedAcksLabels acknowledgement requests for subject deletion announcements.
     * @param requestedAcksTimeout timeout of acknowledgement requests.
     * @param randomizationInterval interval for randomizing the announcement timing.
     * @return the new {@link SubjectAnnouncement}.
     */
    static SubjectAnnouncement of(@Nullable final DittoDuration beforeExpiry,
            final boolean whenDeleted,
            final List<AcknowledgementRequest> requestedAcksLabels,
            @Nullable final DittoDuration requestedAcksTimeout,
            @Nullable final DittoDuration randomizationInterval) {

        return new ImmutableSubjectAnnouncement(beforeExpiry, whenDeleted, requestedAcksLabels,
                requestedAcksTimeout, randomizationInterval);
    }

    /**
     * Returns a new {@link SubjectAnnouncement} with the configuration given in the
     * JSON object.
     *
     * @param jsonObject the JSON representation.
     * @return the new {@link SubjectAnnouncement}.
     */
    static SubjectAnnouncement fromJson(final JsonObject jsonObject) {
        return ImmutableSubjectAnnouncement.fromJson(jsonObject);
    }

    /**
     * Returns the duration before expiry when an announcement should be sent, or an empty optional if no announcement
     * should be sent before expiry.
     *
     * @return duration of the optional announcement window.
     */
    Optional<DittoDuration> getBeforeExpiry();

    /**
     * Returns whether an announcement should be sent when the subject is deleted.
     *
     * @return whether the expiry is expired or not.
     */
    boolean isWhenDeleted();

    /**
     * Returns acknowledgement requests to fulfill for subject deletion announcements before expiry.
     *
     * @return the acknowledgement requests.
     * @since 2.1.0
     */
    List<AcknowledgementRequest> getRequestedAcksLabels();

    /**
     * Returns timeout of acknowledgement requests.
     *
     * @return the timeout.
     * @since 2.1.0
     */
    Optional<DittoDuration> getRequestedAcksTimeout();

    /**
     * Returns interval in which the announcement can be sent earlier in effort to prevent announcement
     * peaks.
     *
     * @return the interval.
     * @since 3.0.0
     */
    Optional<DittoDuration> getRandomizationInterval();

    /**
     * Returns a copy of this object with the field {@code beforeExpiry} replaced.
     *
     * @param beforeExpiry the new value.
     * @return the copy.
     * @since 2.1.0
     */
    SubjectAnnouncement setBeforeExpiry(@Nullable DittoDuration beforeExpiry);

    /**
     * Fields of the JSON representation of a {@code SubjectAnnouncement} object.
     */
    final class JsonFields {

        /**
         * Field to store the duration before expiry when an announcement should be sent.
         */
        public static final JsonFieldDefinition<String> BEFORE_EXPIRY =
                JsonFactory.newStringFieldDefinition("beforeExpiry");

        /**
         * Field to store whether an announcement should be sent upon subject deletion.
         */
        public static final JsonFieldDefinition<Boolean> WHEN_DELETED =
                JsonFactory.newBooleanFieldDefinition("whenDeleted");

        /**
         * Field to store requested acknowledgements for announcements before expiry.
         * @since 2.1.0
         */
        public static final JsonFieldDefinition<JsonArray> REQUESTED_ACKS_LABELS =
                JsonFactory.newJsonArrayFieldDefinition("requestedAcks/labels");

        /**
         * Field to store timeout waiting for requested acknowledgements.
         * @since 2.1.0
         */
        public static final JsonFieldDefinition<String> REQUESTED_ACKS_TIMEOUT =
                JsonFactory.newStringFieldDefinition("requestedAcks/timeout");

        /**
         * Field to store interval in which the announcement can be sent earlier in effort to prevent announcement
         * peaks.
         * @since 3.0.0
         */
        public static final JsonFieldDefinition<String> RANDOMIZATION_INTERVAL =
                JsonFactory.newStringFieldDefinition("randomizationInterval");

        private JsonFields() {}
    }
}
