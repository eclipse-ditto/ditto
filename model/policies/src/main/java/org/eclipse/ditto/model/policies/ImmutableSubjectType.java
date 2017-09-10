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
 * An immutable implementation of {@link SubjectType}.
 */
@Immutable
final class ImmutableSubjectType implements SubjectType {

    private final String subjectType;

    private ImmutableSubjectType(final String subjectType) {
        this.subjectType = subjectType;
    }

    /**
     * Returns a new SubjectType based on the provided string.
     *
     * @param subjectTypeValue the character sequence forming the SubjectType's value.
     * @return a new SubjectType.
     * @throws NullPointerException if {@code subjectTypeValue} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectTypeValue} is empty.
     */
    public static SubjectType of(final CharSequence subjectTypeValue) {
        argumentNotEmpty(subjectTypeValue, "subjectTypeValue");

        return new ImmutableSubjectType(subjectTypeValue.toString());
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
        final ImmutableSubjectType that = (ImmutableSubjectType) o;
        return Objects.equals(subjectType, that.subjectType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectType);
    }

    @Override
    @Nonnull
    public String toString() {
        return subjectType;
    }

}
