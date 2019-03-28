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
package org.eclipse.ditto.services.gateway.security.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link JsonWebToken} for standard JSON Web Tokens.
 */
@Immutable
public final class ImmutableJsonWebToken extends AbstractJsonWebToken {

    private final List<String> authorizationSubjects;

    private ImmutableJsonWebToken(final String authorizationString) {
        super(authorizationString);

        authorizationSubjects = getBody().getValue(JsonFields.USER_ID)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    /**
     * Returns a new {@code ImmutableJsonWebToken} for the given {@code authorizationString}.
     *
     * @param authorizationString the authorization string.
     * @return the ImmutableJsonWebToken.
     * @throws NullPointerException if {@code authorizationString} is {@code null}.
     */
    public static JsonWebToken fromAuthorizationString(final String authorizationString) {
        return new ImmutableJsonWebToken(checkNotNull(authorizationString));
    }

    @Override
    public List<String> getSubjects() {
        return authorizationSubjects;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ImmutableJsonWebToken that = (ImmutableJsonWebToken) o;
        return Objects.equals(authorizationSubjects, that.authorizationSubjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), authorizationSubjects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", authorizationSubjects=" +
                authorizationSubjects + "]";
    }

}
