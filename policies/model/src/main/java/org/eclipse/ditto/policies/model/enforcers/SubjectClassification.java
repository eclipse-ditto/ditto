/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.enforcers;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;

/**
 * Immutable result of classifying authorization subjects into three categories based on their permissions
 * on a specific resource:
 * <ul>
 *   <li>{@code unrestricted} - subjects with full access, no revocations below</li>
 *   <li>{@code partialOnly} - subjects with some grants but not unrestricted (partial minus unrestricted)</li>
 *   <li>{@code effectedGranted} - subjects with grants exactly at the resource level</li>
 * </ul>
 *
 * @since 3.9.0
 */
public final class SubjectClassification {

    private static final SubjectClassification EMPTY = new SubjectClassification(
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    private final Set<AuthorizationSubject> unrestricted;
    private final Set<AuthorizationSubject> partialOnly;
    private final Set<AuthorizationSubject> effectedGranted;

    private SubjectClassification(final Set<AuthorizationSubject> unrestricted,
            final Set<AuthorizationSubject> partialOnly,
            final Set<AuthorizationSubject> effectedGranted) {
        this.unrestricted = Collections.unmodifiableSet(unrestricted);
        this.partialOnly = Collections.unmodifiableSet(partialOnly);
        this.effectedGranted = Collections.unmodifiableSet(effectedGranted);
    }

    /**
     * Creates a new {@code SubjectClassification} from the given sets.
     *
     * @param unrestricted subjects with full access, no revocations below.
     * @param partialOnly subjects with some grants but not unrestricted.
     * @param effectedGranted subjects with grants exactly at the resource level.
     * @return the new SubjectClassification.
     */
    public static SubjectClassification of(final Set<AuthorizationSubject> unrestricted,
            final Set<AuthorizationSubject> partialOnly,
            final Set<AuthorizationSubject> effectedGranted) {
        return new SubjectClassification(unrestricted, partialOnly, effectedGranted);
    }

    /**
     * Returns an empty {@code SubjectClassification} with no subjects in any category.
     *
     * @return the empty SubjectClassification.
     */
    public static SubjectClassification empty() {
        return EMPTY;
    }

    /**
     * Returns the subjects with unrestricted (full) access.
     *
     * @return an unmodifiable set of unrestricted subjects.
     */
    public Set<AuthorizationSubject> getUnrestricted() {
        return unrestricted;
    }

    /**
     * Returns the subjects with partial access only (not unrestricted).
     *
     * @return an unmodifiable set of partial-only subjects.
     */
    public Set<AuthorizationSubject> getPartialOnly() {
        return partialOnly;
    }

    /**
     * Returns the subjects with grants exactly at the resource level.
     *
     * @return an unmodifiable set of effected granted subjects.
     */
    public Set<AuthorizationSubject> getEffectedGranted() {
        return effectedGranted;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SubjectClassification that = (SubjectClassification) o;
        return Objects.equals(unrestricted, that.unrestricted) &&
                Objects.equals(partialOnly, that.partialOnly) &&
                Objects.equals(effectedGranted, that.effectedGranted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unrestricted, partialOnly, effectedGranted);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "unrestricted=" + unrestricted +
                ", partialOnly=" + partialOnly +
                ", effectedGranted=" + effectedGranted +
                "]";
    }

}
