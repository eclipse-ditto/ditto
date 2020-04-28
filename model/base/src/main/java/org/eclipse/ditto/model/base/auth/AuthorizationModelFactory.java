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
package org.eclipse.ditto.model.base.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * Factory that creates new {@code authorization} objects.
 */
@Immutable
public final class AuthorizationModelFactory {

    /*
     * Inhibit instantiation of this utility class.
     */
    private AuthorizationModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new immutable {@link AuthorizationSubject} with the given identifier.
     *
     * @param identifier the identifier of the new authorization subject.
     * @return the new {@code AuthorizationSubject}.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     * @throws IllegalArgumentException if {@code identifier} is empty
     */
    public static AuthorizationSubject newAuthSubject(final CharSequence identifier) {
        return ImmutableAuthorizationSubject.of(identifier);
    }

    /**
     * Returns a new immutable empty {@link AuthorizationContext}.
     *
     * @return the new {@code AuthorizationContext}.
     */
    public static AuthorizationContext emptyAuthContext() {
        return ImmutableAuthorizationContext.of(DittoAuthorizationContextType.UNSPECIFIED, Collections.emptyList());
    }

    /**
     * Returns a new immutable {@link AuthorizationContext} with the given authorization subjects.
     *
     * @param type the mandatory type defining which "kind" of authorization context should be created.
     * @param authorizationSubject the mandatory authorization subject of the new authorization context.
     * @param furtherAuthorizationSubjects additional authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    public static AuthorizationContext newAuthContext(final AuthorizationContextType type,
            final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {

        return ImmutableAuthorizationContext.of(type, authorizationSubject, furtherAuthorizationSubjects);
    }

    /**
     * Returns a new immutable {@link AuthorizationContext} with the given authorization subjects.
     *
     * @param authorizationSubject the mandatory authorization subject of the new authorization context.
     * @param furtherAuthorizationSubjects additional authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated as of 1.1.0, please use
     * {@link #newAuthContext(AuthorizationContextType, AuthorizationSubject, AuthorizationSubject...)} instead
     */
    @Deprecated
    public static AuthorizationContext newAuthContext(final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {

        return ImmutableAuthorizationContext.of(DittoAuthorizationContextType.UNSPECIFIED, authorizationSubject,
                furtherAuthorizationSubjects);
    }

    /**
     * Returns a new immutable {@link AuthorizationContext} based on the given JSON object.
     *
     * @param jsonObject the JSON object representation of an authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws DittoJsonException if {@code jsonObject} is {@code null} or cannot be parsed.
     */
    public static AuthorizationContext newAuthContext(final JsonObject jsonObject) {
        return DittoJsonException.wrapJsonRuntimeException(() -> ImmutableAuthorizationContext.fromJson(jsonObject));
    }

    /**
     * Returns a new immutable {@link AuthorizationContext} with the given authorization subjects.
     *
     * @param type the mandatory type defining which "kind" of authorization context should be created.
     * @param authorizationSubjects the authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    public static AuthorizationContext newAuthContext(final AuthorizationContextType type,
            final Iterable<AuthorizationSubject> authorizationSubjects) {

        final List<AuthorizationSubject> authSubjectsList = new ArrayList<>();
        authorizationSubjects.forEach(authSubjectsList::add);
        return ImmutableAuthorizationContext.of(type, authSubjectsList);
    }

    /**
     * Returns a new immutable {@link AuthorizationContext} with the given authorization subjects.
     *
     * @param authorizationSubjects the authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if {@code authorizationSubjects} is {@code null}.
     * @deprecated as of 1.1.0, please use {@link #newAuthContext(AuthorizationContextType, java.lang.Iterable)} instead
     */
    @Deprecated
    public static AuthorizationContext newAuthContext(final Iterable<AuthorizationSubject> authorizationSubjects) {
        final List<AuthorizationSubject> authSubjectsList = new ArrayList<>();
        authorizationSubjects.forEach(authSubjectsList::add);
        return ImmutableAuthorizationContext.of(DittoAuthorizationContextType.UNSPECIFIED, authSubjectsList);
    }

}
