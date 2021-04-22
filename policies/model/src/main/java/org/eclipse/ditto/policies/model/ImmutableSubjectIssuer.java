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
     */
    public static SubjectIssuer of(final CharSequence subjectIssuerValue) {
        checkNotNull(subjectIssuerValue, "subjectIssuerValue");

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
