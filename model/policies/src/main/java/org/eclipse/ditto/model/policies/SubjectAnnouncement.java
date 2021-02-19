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
package org.eclipse.ditto.model.policies;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoDuration;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents announcement settings of a {@link Subject}.
 *
 * @since 2.0.0
 */
@Immutable
public interface SubjectAnnouncement extends Jsonifiable<JsonObject> {

    /**
     * Returns a new {@link org.eclipse.ditto.model.policies.SubjectAnnouncement} with the given configuration.
     *
     * @param beforeExpiry duration before expiry when an announcement should be sent, or null if no announcement should
     * be sent.
     * @param whenDeleted whether an announcement should be sent when the subject is deleted.
     * @return the new {@link org.eclipse.ditto.model.policies.SubjectAnnouncement}.
     */
    static SubjectAnnouncement of(@Nullable final DittoDuration beforeExpiry, final boolean whenDeleted) {
        return new ImmutableSubjectAnnouncement(beforeExpiry, whenDeleted);
    }

    /**
     * Returns a new {@link org.eclipse.ditto.model.policies.SubjectAnnouncement} with the configuration given in the
     * JSON object.
     *
     * @param jsonObject the JSON representation.
     * @return the new {@link org.eclipse.ditto.model.policies.SubjectAnnouncement}.
     */
    static SubjectAnnouncement fromJson(final JsonObject jsonObject) {
        return ImmutableSubjectAnnouncement.fromJson(jsonObject);
    }

    /**
     * Returns a {@link org.eclipse.ditto.model.policies.SubjectAnnouncement} that does not expect any announcement.
     *
     * @return the empty {@link org.eclipse.ditto.model.policies.SubjectAnnouncement}.
     */
    static SubjectAnnouncement empty() {
        return ImmutableSubjectAnnouncement.empty();
    }

    /**
     * Return whether no announcement is expected.
     *
     * @return whether no announcement is expected.
     */
    boolean isEmpty();

    /**
     * Returns the duration before expiry when an announcement should be sent, or an empty optional if no notification
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

        private JsonFields() {}
    }
}
