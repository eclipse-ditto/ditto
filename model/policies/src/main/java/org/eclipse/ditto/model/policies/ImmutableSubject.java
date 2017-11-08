/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link Subject}.
 */
@Immutable
final class ImmutableSubject implements Subject {

    private final SubjectId subjectId;
    private final SubjectType subjectType;

    private ImmutableSubject(final SubjectId theSubjectId, final SubjectType theSubjectType) {
        subjectId = checkNotNull(theSubjectId, "subjectId");
        subjectType = checkNotNull(theSubjectType, "subjectType");
    }

    /**
     * Returns a new {@code Subject} object with the given {@code subjectId} and
     * subject type {@link SubjectType#UNKNOWN}.
     *
     * @param subjectId the ID of the new Subject.
     * @return a new {@code Subject} object.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     */
    public static Subject of(final SubjectId subjectId) {
        return new ImmutableSubject(subjectId, SubjectType.UNKNOWN);
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
        return new ImmutableSubject(subjectId, subjectType);
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
     */
    public static Subject fromJson(final CharSequence subjectIssuerWithId, final JsonObject jsonObject) {
        checkNotNull(subjectIssuerWithId, "Subject ID");
        checkNotNull(jsonObject, "JSON object");

        final String subjectTypeValue = jsonObject.getValue(JsonFields.TYPE)
                .orElseThrow(() -> new DittoJsonException(JsonMissingFieldException.newBuilder()
                        .message("The JSON object is either empty or contains only fields with the schema version.")
                        .build()));

        return of(SubjectId.newInstance(subjectIssuerWithId), ImmutableSubjectType.of(subjectTypeValue));
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
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate)
                .set(JsonFields.TYPE, subjectType.toString(), predicate)
                .build();
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
        return Objects.equals(subjectId, that.subjectId) && Objects.equals(subjectType, that.subjectType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, subjectType);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "subjectId=" + subjectId +
                ", subjectType=" + subjectType +
                "]";
    }

}
