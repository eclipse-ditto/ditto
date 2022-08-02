/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * Represents a single Subject in the {@code Subjects} of a {@link PolicyEntry}.
 */
public interface Subject extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code Subject} with the specified {@code issuer}, {@code subject} and
     * subject type {@link SubjectType#GENERATED}.
     *
     * @param issuer the character sequence for the SubjectId's {@code issuer}.
     * @param subject the character sequence for the SubjectId's {@code subject}.
     * @return the new {@code Subject}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Subject newInstance(final SubjectIssuer issuer, final CharSequence subject) {
        return PoliciesModelFactory.newSubject(SubjectId.newInstance(issuer, subject));
    }

    /**
     * Returns a new {@code Subject} with the specified {@code issuer}, {@code subject} and {@code subjectType}.
     *
     * @param issuer the character sequence for the SubjectId's {@code issuer}.
     * @param subject the character sequence for the SubjectId's {@code subject}.
     * @param subjectType the SubjectType of the new Subject to create.
     * @return the new {@code Subject}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Subject newInstance(final SubjectIssuer issuer, final CharSequence subject, final SubjectType subjectType) {
        return PoliciesModelFactory.newSubject(SubjectId.newInstance(issuer, subject), subjectType);
    }

    /**
     * Returns a new {@code Subject} with the specified {@code subjectIssuerWithId} and {@code subjectType}.
     *
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}") of the Subject to create.
     * @param subjectType the SubjectType of the new Subject to create.
     * @return the new {@code Subject}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Subject newInstance(final CharSequence subjectIssuerWithId, final SubjectType subjectType) {
        return PoliciesModelFactory.newSubject(SubjectId.newInstance(subjectIssuerWithId), subjectType);
    }

    /**
     * Returns a new {@code Subject} object with the given {@code subjectId} and
     * subject type {@link SubjectType#GENERATED}.
     *
     * @param subjectId the ID of the new Subject.
     * @return a new {@code Subject} object.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     */
    static Subject newInstance(final SubjectId subjectId) {
        return PoliciesModelFactory.newSubject(subjectId);
    }

    /**
     * Returns a new {@code Subject} with the specified {@code subjectId} and {@code subjectType}.
     *
     * @param subjectId the ID of the new Subject to create.
     * @param subjectType the SubjectType of the new Subject to create.
     * @return the new {@code Subject}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Subject newInstance(final SubjectId subjectId, final SubjectType subjectType) {
        return PoliciesModelFactory.newSubject(subjectId, subjectType);
    }

    /**
     * Returns a new {@code Subject} with the specified {@code subjectId} and {@code subjectType}.
     *
     * @param subjectId the ID of the new Subject to create.
     * @param subjectType the SubjectType of the new Subject to create.
     * @param subjectExpiry the expiry timestamp of the new Subject.
     * @return the new {@code Subject}.
     * @throws NullPointerException if the {@code subjectId} or {@code subjectType} argument is {@code null}.
     * @since 2.0.0
     */
    static Subject newInstance(final SubjectId subjectId, final SubjectType subjectType,
            @Nullable final SubjectExpiry subjectExpiry) {
        return PoliciesModelFactory.newSubject(subjectId, subjectType, subjectExpiry);
    }

    /**
     * Returns a new {@code Subject}.
     *
     * @param subjectId the ID of the new Subject to create.
     * @param subjectType the SubjectType of the new Subject to create.
     * @param subjectExpiry the expiry timestamp of the new Subject.
     * @param subjectAnnouncement settings for announcements to be made about this subject.
     * @return the new {@code Subject}.
     * @throws NullPointerException if the {@code subjectId} or {@code subjectType} argument is {@code null}.
     * @since 2.0.0
     */
    static Subject newInstance(final SubjectId subjectId,
            final SubjectType subjectType,
            @Nullable final SubjectExpiry subjectExpiry,
            @Nullable final SubjectAnnouncement subjectAnnouncement) {

        return ImmutableSubject.of(subjectId, subjectType, subjectExpiry, subjectAnnouncement);
    }

    /**
     * Subject is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of Subject.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the ID of this Subject.
     *
     * @return the ID.
     */
    SubjectId getId();

    /**
     * Returns the type of this Subject.
     *
     * @return the type.
     */
    SubjectType getType();

    /**
     * Returns the optional expiry timestamp of this Subject.
     * Once this time was reached, the Subjects is automatically removed from the Policy entry.
     *
     * @return the expiry timestamp of this Subject.
     * @since 2.0.0
     */
    Optional<SubjectExpiry> getExpiry();

    /**
     * Returns the configuration of announcements to send for this Subject.
     *
     * @return the announcement config of this Subject.
     * @since 2.0.0
     */
    Optional<SubjectAnnouncement> getAnnouncement();

    /**
     * Returns all non-hidden marked fields of this Subject.
     *
     * @return a JSON object representation of this Subject including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@link JsonField}s of a Subject.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the Subject's type.
         */
        public static final JsonFieldDefinition<String> TYPE =
                JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing configuration for announcements related to the Subject.
         *
         * @since 2.0.0
         */
        public static final JsonFieldDefinition<JsonObject> ANNOUNCEMENT =
                JsonFactory.newJsonObjectFieldDefinition("announcement", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Subject's expiry time.
         *
         * @since 2.0.0
         */
        public static final JsonFieldDefinition<String> EXPIRY =
                JsonFactory.newStringFieldDefinition("expiry", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
