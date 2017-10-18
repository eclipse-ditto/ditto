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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link SubjectId}.
 */
@Immutable
final class ImmutableSubjectId implements SubjectId {

    /**
     * Ignore the Delimiter {@link #ISSUER_DELIMITER} in URLs like {@code http://}.
     */
    private static final String IGNORED_DELIMITER = "://";

    private final SubjectIssuer issuer;
    private final String subject;

    private ImmutableSubjectId(final SubjectIssuer issuer, final String subject) {
        this.issuer = issuer;
        this.subject = subject;
    }

    /**
     * Returns a new SubjectId based on the provided strings.
     *
     * @param issuer the SubjectId's {@code issuer}.
     * @param subject the character sequence for the SubjectId's {@code subject}.
     * @return a new SubjectId.
     * @throws NullPointerException if {@code issuer} or {@code subject} is {@code null}.
     * @throws IllegalArgumentException if {@code issuer} or {@code subject} is empty.
     */
    public static SubjectId of(final SubjectIssuer issuer, final CharSequence subject) {
        checkNotNull(issuer, "issuer");
        argumentNotEmpty(subject, "subject");
        return new ImmutableSubjectId(issuer, subject.toString());
    }

    /**
     * Returns a new SubjectId based on the provided string.
     *
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}").
     * @return a new SubjectId.
     * @throws NullPointerException if {@code subjectIssuerWithId} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectIssuerWithId} is empty.
     */
    public static SubjectId of(final CharSequence subjectIssuerWithId) {
        argumentNotEmpty(subjectIssuerWithId, "subjectIssuerWithId");

        final String subjectIdAsString = subjectIssuerWithId.toString();
        if (!subjectIdAsString.contains(ISSUER_DELIMITER)) {
            throw SubjectIdInvalidException.newBuilder(subjectIssuerWithId).build();
        }

        final int ignoredDelimiterIndex = subjectIdAsString.indexOf(IGNORED_DELIMITER);
        final int lastDelimiter = ignoredDelimiterIndex >= 0
                ? subjectIdAsString.indexOf(ISSUER_DELIMITER, ignoredDelimiterIndex + IGNORED_DELIMITER.length())
                : subjectIdAsString.indexOf(ISSUER_DELIMITER);
        final SubjectIssuer issuer =
                PoliciesModelFactory.newSubjectIssuer(subjectIdAsString.substring(0, lastDelimiter));
        final String subject = subjectIdAsString.replaceFirst(issuer.toString() + ISSUER_DELIMITER, "");

        return of(issuer, subject);
    }

    @Override
    public SubjectIssuer getIssuer() {
        return issuer;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSubjectId that = (ImmutableSubjectId) o;
        return Objects.equals(issuer, that.issuer) && Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, subject);
    }

    @Override
    @Nonnull
    public String toString() {
        return issuer + ISSUER_DELIMITER + subject;
    }

}
