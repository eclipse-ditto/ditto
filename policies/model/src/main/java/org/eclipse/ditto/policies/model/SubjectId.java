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

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Represents a {@link Subject}'s ID.
 */
public interface SubjectId extends CharSequence {

    /**
     * The delimiter separating issuer and actual subject.
     */
    String ISSUER_DELIMITER = ":";

    /**
     * Returns Subject ID for the given {@code issuer} and {@code subject} sequences.
     *
     * @param issuer the character sequence for the SubjectId's {@code issuer}.
     * @param subject the character sequence for the SubjectId's {@code subject}.
     * @return a new SubjectId.
     * @throws NullPointerException if {@code issuer} or {@code subject} is {@code null}.
     * @throws IllegalArgumentException if {@code issuer} or {@code subject} is empty.
     */
    static SubjectId newInstance(final SubjectIssuer issuer, final CharSequence subject) {
        return PoliciesModelFactory.newSubjectId(issuer, subject);
    }

    /**
     * Returns Subject ID for the given character sequence. If the given key value is already a Subject ID, this is
     * immediately properly cast and returned.
     *
     * @param issuerWithSubject the character sequence value of the Subject ID to be created consisting of an issuer
     * separated by a "{@value #ISSUER_DELIMITER}" from the actual subject value.
     * @return a new Subject ID with {@code issuerWithSubject} as its value.
     * @throws NullPointerException if {@code issuerWithSubject} is {@code null}.
     * @throws IllegalArgumentException if {@code issuerWithSubject} is empty.
     */
    static SubjectId newInstance(final CharSequence issuerWithSubject) {
        return PoliciesModelFactory.newSubjectId(issuerWithSubject);
    }

    /**
     * Returns the JsonFieldDefinition for this Subject.
     *
     * @return the JsonFieldDefinition for this Subject.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    default JsonFieldDefinition getJsonFieldDefinition() {
        return JsonFactory.newStringFieldDefinition(this, FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

    /**
     * Returns the issuer of the subjectId.
     *
     * @return the issuer of the subjectId.
     */
    SubjectIssuer getIssuer();

    /**
     * Returns the subject value of the subjectId. This is the authenticated subject.
     *
     * @return the subject value of the subjectId.
     */
    String getSubject();

    @Override
    @Nonnull
    String toString();

}
