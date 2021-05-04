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
package org.eclipse.ditto.policies.model.enforcers.tree;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.enforcers.DefaultEffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;

/**
 * A mutable builder with a fluent API for a {@link org.eclipse.ditto.policies.model.enforcers.EffectedSubjects} object.
 *
 * @since 1.1.0
 */
@NotThreadSafe
final class EffectedSubjectsBuilder {

    private final Set<AuthorizationSubject> grantedSubjects;
    private final Set<AuthorizationSubject> revokedSubjects;

    EffectedSubjectsBuilder() {
        grantedSubjects = new HashSet<>();
        revokedSubjects = new HashSet<>();
    }

    /**
     * Adds the specified subject which is known to has granted permissions.
     *
     * @param grantedSubject the subject to be added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code grantedSubject} is {@code null}.
     * @throws IllegalArgumentException if {@code grantedSubject} is empty.
     */
    public EffectedSubjectsBuilder withGranted(final AuthorizationSubject grantedSubject) {
        if (!revokedSubjects.contains(grantedSubject)) {
            grantedSubjects.add(grantedSubject);
        }
        return this;
    }

    /**
     * Adds the specified subject which is known to has revoked permissions.
     *
     * @param revokedSubject the subject to be added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code grantedSubject} is {@code null}.
     * @throws IllegalArgumentException if {@code grantedSubject} is empty.
     */
    public EffectedSubjectsBuilder withRevoked(final AuthorizationSubject revokedSubject) {
        revokedSubjects.add(revokedSubject);
        grantedSubjects.remove(revokedSubject);
        return this;
    }

    /**
     * Builds the new {@code ImmutableEffectedSubjectIds} instance.
     *
     * @return the instance.
     */
    public EffectedSubjects build() {
        return DefaultEffectedSubjects.of(grantedSubjects, revokedSubjects);
    }

}
