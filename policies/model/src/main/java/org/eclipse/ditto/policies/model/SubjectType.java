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

/**
 * Represents a {@link Subject} type used in a subject for documenting what type it is.
 */
public interface SubjectType extends CharSequence {

    /**
     * Unknown {@link Subject} type.
     */
    SubjectType UNKNOWN = PoliciesModelFactory.newSubjectType("unknown");

    /**
     * Generated {@link Subject} type.
     */
    SubjectType GENERATED = PoliciesModelFactory.newSubjectType("generated");

    /**
     * Returns a new {@link SubjectType} with the specified {@code subjectType}.
     *
     * @param subjectType the SubjectType char sequence.
     * @return the new {@link SubjectType}.
     * @throws NullPointerException if {@code subjectType} is {@code null}.
     */
    static SubjectType newInstance(final CharSequence subjectType) {
        return PoliciesModelFactory.newSubjectType(subjectType);
    }

    @Override
    String toString();

}
