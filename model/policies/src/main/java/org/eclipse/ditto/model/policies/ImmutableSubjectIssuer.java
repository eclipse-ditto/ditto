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

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link SubjectIssuer}.
 */
@Immutable
final class ImmutableSubjectIssuer implements SubjectIssuer {

    private final String subjectIssuer;

    private ImmutableSubjectIssuer(final String subjectIssuer) {
        this.subjectIssuer = subjectIssuer;
    }

    /**
     * Returns a new SubjectIssuer based on the provided string.
     *
     * @param subjectIssuerValue the character sequence forming the SubjectType's value.
     * @return a new SubjectIssuer.
     * @throws NullPointerException if {@code subjectIssuerValue} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectIssuerValue} is empty.
     */
    public static SubjectIssuer of(final CharSequence subjectIssuerValue) {
        argumentNotEmpty(subjectIssuerValue, "subjectIssuerValue");

        return new ImmutableSubjectIssuer(subjectIssuerValue.toString());
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
        final ImmutableSubjectIssuer that = (ImmutableSubjectIssuer) o;
        return Objects.equals(subjectIssuer, that.subjectIssuer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectIssuer);
    }

    @Override
    @Nonnull
    public String toString() {
        return subjectIssuer;
    }

}
