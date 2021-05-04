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
package org.eclipse.ditto.jwt.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link JsonWebToken} for standard JSON Web Tokens.
 */
@Immutable
public final class ImmutableJsonWebToken extends AbstractJsonWebToken {

    private final List<String> authorizationSubjects;

    private ImmutableJsonWebToken(final String token) {
        super(token);

        authorizationSubjects = getBody().getValue(JsonFields.SUB)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    /**
     * Returns a new {@code ImmutableJsonWebToken} for the given {@code authorization}.
     *
     * @param authorization the authorization string.
     * @return the ImmutableJsonWebToken.
     * @throws NullPointerException if {@code authorization} is {@code null}.
     */
    public static JsonWebToken fromAuthorization(final String authorization) {
        final String token = getTokenFromAuthorizationString(authorization);
        return fromToken(token);
    }

    private static String getTokenFromAuthorizationString(final String authorizationString) {
        checkNotNull(authorizationString, "authorizationString");
        checkNotEmpty(authorizationString, "authorizationString");

        final String[] authorizationStringSplit = authorizationString.split(" ");
        if (2 != authorizationStringSplit.length) {
            throw JwtInvalidException.newBuilder()
                    .description("The Authorization Header is invalid!")
                    .build();
        }
        return authorizationStringSplit[1];
    }

    /**
     * Returns a new {@code ImmutableJsonWebToken} for the given {@code token}.
     *
     * @param token the token string.
     * @return the ImmutableJsonWebToken.
     * @throws NullPointerException if {@code token} is {@code null}.
     * @throws IllegalArgumentException if {@code token} is empty.
     */
    public static JsonWebToken fromToken(final String token) {
        return new ImmutableJsonWebToken(argumentNotEmpty(token, "token"));
    }

    @Override
    public List<String> getSubjects() {
        return authorizationSubjects;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
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
