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
        return ImmutableAuthorizationContext.of(Collections.emptyList());
    }

    /**
     * Returns a new immutable {@link AuthorizationContext} with the given authorization subjects.
     *
     * @param authorizationSubject the mandatory authorization subject of the new authorization context.
     * @param furtherAuthorizationSubjects additional authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AuthorizationContext newAuthContext(final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {
        return ImmutableAuthorizationContext.of(authorizationSubject, furtherAuthorizationSubjects);
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
     * @param authorizationSubjects the authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if {@code authorizationSubjects} is {@code null}.
     */
    public static AuthorizationContext newAuthContext(final Iterable<AuthorizationSubject> authorizationSubjects) {
        final List<AuthorizationSubject> authSubjectsList = new ArrayList<>();
        authorizationSubjects.forEach(authSubjectsList::add);
        return ImmutableAuthorizationContext.of(authSubjectsList);
    }
}
