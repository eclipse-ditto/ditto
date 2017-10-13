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

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

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
