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
package org.eclipse.ditto.base.model.auth;


import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link org.eclipse.ditto.base.model.auth.AuthorizationSubject}.
 */
@Immutable
final class ImmutableAuthorizationSubject implements AuthorizationSubject {

    private final String id;

    private ImmutableAuthorizationSubject(final CharSequence theId) {
        id = theId.toString();
    }

    /**
     * Returns a new {@code AuthorizationSubject} object with the given ID.
     *
     * @param id the ID of the Authorization Subject to be created.
     * @return a new Authorization Subject with the given ID.
     * @throws NullPointerException if {@code id} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    public static ImmutableAuthorizationSubject of(final CharSequence id) {
        final String argumentName = "identifier of the new authorization subject";
        checkNotNull(id, argumentName);
        checkNotEmpty(id, argumentName);

        return new ImmutableAuthorizationSubject(id);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAuthorizationSubject that = (ImmutableAuthorizationSubject) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Returns the ID of this Authorization Subject.
     *
     * @return this Authorization Subject's ID.
     * @see #getId()
     */
    @Override
    public String toString() {
        return getId();
    }

}
