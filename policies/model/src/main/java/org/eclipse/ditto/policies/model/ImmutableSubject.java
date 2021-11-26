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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * An immutable implementation of {@link Subject}.
 */
@Immutable
final class ImmutableSubject implements Subject {

    private final SubjectId subjectId;
    private final SubjectType subjectType;
    @Nullable private final SubjectExpiry subjectExpiry;
    @Nullable private final SubjectAnnouncement subjectAnnouncement;

    private ImmutableSubject(final SubjectId subjectId,
            final SubjectType subjectType,
            @Nullable final SubjectExpiry subjectExpiry,
            @Nullable final SubjectAnnouncement subjectAnnouncement) {

        this.subjectId = checkNotNull(subjectId, "subjectId");
        this.subjectType = checkNotNull(subjectType, "subjectType");
        this.subjectExpiry = subjectExpiry;
        this.subjectAnnouncement = subjectAnnouncement;
    }

    /**
     * Returns a new {@code Subject} object with the given {@code subjectId} and
     * subject type {@link SubjectType#GENERATED}.
     *
     * @param subjectId the ID of the new Subject.
     * @return a new {@code Subject} object.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     */
    public static Subject of(final SubjectId subjectId) {
        return new ImmutableSubject(subjectId, SubjectType.GENERATED, null, null);
    }

    /**
     * Returns a new {@code Subject} object of the given {@code subjectId} and {@code subjectType}.
     *
     * @param subjectId the ID of the new Subject.
     * @param subjectType the type of the new Subject.
     * @return a new {@code Subject} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Subject of(final SubjectId subjectId, final SubjectType subjectType) {
        return new ImmutableSubject(subjectId, subjectType, null, null);
    }

    /**
     * Returns a new {@code Subject} object of the given {@code subjectId}, {@code subjectType} and
     * {@code subjectExpiry}.
     *
     * @param subjectId the ID of the new Subject.
     * @param subjectType the type of the new Subject.
     * @param subjectExpiry the expiry timestamp of the new Subject.
     * @return a new {@code Subject} object.
     * @throws NullPointerException if the {@code subjectId} or {@code subjectType} argument is {@code null}.
     * @since 2.0.0
     */
    public static Subject of(final SubjectId subjectId,
            final SubjectType subjectType,
            @Nullable final SubjectExpiry subjectExpiry) {

        return new ImmutableSubject(subjectId, subjectType, subjectExpiry, null);
    }

    /**
     * Returns a new {@code Subject} object of the given {@code subjectId}, {@code subjectType},
     * {@code subjectExpiry} and {@code subjectAnnouncement}.
     *
     * @param subjectId the ID of the new Subject.
     * @param subjectType the type of the new Subject.
     * @param subjectExpiry the expiry timestamp of the new Subject.
     * @param subjectAnnouncement any announcement to publish for this subject.
     * @return a new {@code Subject} object.
     * @throws NullPointerException if the {@code subjectId} or {@code subjectType} argument is {@code null}.
     * @since 2.0.0
     */
    public static Subject of(final SubjectId subjectId,
            final SubjectType subjectType,
            @Nullable final SubjectExpiry subjectExpiry,
            @Nullable final SubjectAnnouncement subjectAnnouncement) {

        return new ImmutableSubject(subjectId, subjectType, subjectExpiry, subjectAnnouncement);
    }

    /**
     * Creates a new {@code Subject} object from the specified JSON object.
     *
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}" of the Subject to be created.
     * @param jsonObject a JSON object which provides the data for the Subject to be created.
     * @return a new Subject which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject}
     * <ul>
     *     <li>is empty,</li>
     *     <li>contains only a field with the schema version</li>
     *     <li>or it contains more than two fields.</li>
     * </ul>
     * @throws SubjectExpiryInvalidException if the provided {@code expiry} could not be parsed as ISO-8601 timestamp.
     */
    public static Subject fromJson(final CharSequence subjectIssuerWithId, final JsonObject jsonObject) {
        checkNotNull(subjectIssuerWithId, "subjectIssuerWithId");
        checkNotNull(jsonObject, "jsonObject");

        final String subjectTypeValue = jsonObject.getValue(JsonFields.TYPE)
                .orElseThrow(() -> new DittoJsonException(JsonMissingFieldException.newBuilder()
                        .message("The 'type' for the 'subject' is missing.")
                        .build()));

        final SubjectExpiry subjectExpiry = jsonObject.getValue(JsonFields.EXPIRY)
                .map(SubjectExpiry::newInstance)
                .orElse(null);
        final SubjectAnnouncement subjectAnnouncement = jsonObject.getValue(JsonFields.ANNOUNCEMENT)
                .map(SubjectAnnouncement::fromJson)
                .orElse(null);

        return new ImmutableSubject(SubjectId.newInstance(subjectIssuerWithId),
                ImmutableSubjectType.of(subjectTypeValue), subjectExpiry, subjectAnnouncement);
    }

    @Override
    public SubjectId getId() {
        return subjectId;
    }

    @Override
    public SubjectType getType() {
        return subjectType;
    }

    @Override
    public Optional<SubjectExpiry> getExpiry() {
        return Optional.ofNullable(subjectExpiry);
    }

    @Override
    public Optional<SubjectAnnouncement> getAnnouncement() {
        return Optional.ofNullable(subjectAnnouncement);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.TYPE, subjectType.toString(), predicate);
        if (null != subjectExpiry) {
            jsonObjectBuilder.set(JsonFields.EXPIRY, subjectExpiry.toString());
        }
        if (null != subjectAnnouncement) {
            jsonObjectBuilder.set(JsonFields.ANNOUNCEMENT, subjectAnnouncement.toJson());
        }
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSubject that = (ImmutableSubject) o;
        return Objects.equals(subjectId, that.subjectId) &&
                Objects.equals(subjectType, that.subjectType) &&
                Objects.equals(subjectExpiry, that.subjectExpiry) &&
                Objects.equals(subjectAnnouncement, that.subjectAnnouncement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, subjectType, subjectExpiry, subjectAnnouncement);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "subjectId=" + subjectId +
                ", subjectType=" + subjectType +
                ", subjectExpiry=" + subjectExpiry +
                ", subjectAnnouncement=" + subjectAnnouncement +
                "]";
    }

}
