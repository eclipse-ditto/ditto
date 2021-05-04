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
 * Represents a {@link Subject} issuer who has issued the subject.
 */
public interface SubjectIssuer extends CharSequence {

    /**
     * Returns a new {@link SubjectIssuer} with the specified {@code subjectIssuer}.
     *
     * @param subjectIssuer the SubjectIssuer char sequence.
     * @return the new {@link SubjectIssuer}.
     * @throws NullPointerException if {@code subjectIssuer} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectIssuer} is empty.
     */
    static SubjectIssuer newInstance(final CharSequence subjectIssuer) {
        return ImmutableSubjectIssuer.of(subjectIssuer);
    }

    /**
     * The issuer for authentication subjects provided by google.
     */
    SubjectIssuer GOOGLE = PoliciesModelFactory.newSubjectIssuer("google");

    /**
     * The issuer for authentication subjects provided when integrating with external systems.
     */
    SubjectIssuer INTEGRATION = PoliciesModelFactory.newSubjectIssuer("integration");

    @Override
    String toString();

}
