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
package org.eclipse.ditto.model.base.auth;


import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link AuthorizationSubject}.
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
