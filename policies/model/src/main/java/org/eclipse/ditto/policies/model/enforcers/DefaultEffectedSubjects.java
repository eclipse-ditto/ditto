/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;

/**
 * Immutable default implementation of {@link EffectedSubjects}.
 */
@Immutable
public final class DefaultEffectedSubjects implements EffectedSubjects {

    private final Set<AuthorizationSubject> granted;
    private final Set<AuthorizationSubject> revoked;

    private DefaultEffectedSubjects(final Set<AuthorizationSubject> granted, final Set<AuthorizationSubject> revoked) {
        this.granted = granted;
        this.revoked = revoked;
    }

    /**
     * Returns an instance of {@code DefaultEffectedSubjects} containing the given AuthorizationSubjects.
     *
     * @param granted the authorization subjects which have granted permissions.
     * @param revoked the authorization subjects which have revoked permissions.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultEffectedSubjects of(final Collection<AuthorizationSubject> granted,
            final Collection<AuthorizationSubject> revoked) {

        return new DefaultEffectedSubjects(makeUnmodifiable(checkNotNull(granted, "granted")),
                makeUnmodifiable(checkNotNull(revoked, "revoked")));
    }

    private static Set<AuthorizationSubject> makeUnmodifiable(final Collection<AuthorizationSubject> subjectIds) {
        return Collections.unmodifiableSet(new HashSet<>(subjectIds));
    }

    @Override
    public Set<AuthorizationSubject> getGranted() {
        return granted;
    }

    @Override
    public Set<AuthorizationSubject> getRevoked() {
        return revoked;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultEffectedSubjects that = (DefaultEffectedSubjects) o;
        return granted.equals(that.granted) && revoked.equals(that.revoked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(granted, revoked);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "granted=" + granted +
                ", revoked=" + revoked +
                "]";
    }

}
