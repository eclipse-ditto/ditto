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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.common.Validator;
import org.eclipse.ditto.base.model.entity.validation.NoControlCharactersValidator;

/**
 * An immutable implementation of {@link SubjectId}.
 */
@Immutable
final class ImmutableSubjectId implements SubjectId {

    /**
     * Ignore the Delimiter {@link #ISSUER_DELIMITER} in URLs like {@code http://}.
     */
    private static final String IGNORED_DELIMITER = "://";

    private static final SubjectIssuer EMPTY_ISSUER = PoliciesModelFactory.newSubjectIssuer("");

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

        final String subjectIdAsString = issuer + ":" + subject;
        final Validator validator = NoControlCharactersValidator.getInstance(subjectIdAsString);
        if (!validator.isValid()) {
            throw SubjectIdInvalidException.newBuilder(subjectIdAsString)
                    .description(validator.getReason().orElse(null))
                    .build();
        }

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

        if (Placeholders.containsAnyPlaceholder(subjectIssuerWithId)) {
            // in case of placeholders, just use the whole input as subject, use an empty issuer
            //  reason: the placeholder contains a ":" which would conflict with the ISSUE_DELIMITER separating the
            //  issuer fom the subject
            return of(EMPTY_ISSUER, subjectIssuerWithId);
        }

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
        final String subject = subjectIdAsString.substring(lastDelimiter + 1);

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
        if (issuer.length() == 0) {
            return subject;
        }

        return issuer + ISSUER_DELIMITER + subject;
    }

}
