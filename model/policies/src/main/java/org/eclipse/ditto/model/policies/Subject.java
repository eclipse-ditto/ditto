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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents a single Subject in the {@code Subjects} of a {@link PolicyEntry}.
 */
public interface Subject extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code Subject} with the specified {@code issuer}, {@code subject} and
     * subject type {@link SubjectType#UNKNOWN}.
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
     * subject type {@link SubjectType#UNKNOWN}.
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
     * Returns all non hidden marked fields of this Subject.
     *
     * @return a JSON object representation of this Subject including only non hidden marked fields.
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
         * JSON field containing the {@link JsonSchemaVersion} of a Subject.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Subject's type.
         */
        public static final JsonFieldDefinition<String> TYPE =
                JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
