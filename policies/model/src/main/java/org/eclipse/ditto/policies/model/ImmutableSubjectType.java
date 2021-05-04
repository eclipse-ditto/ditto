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
