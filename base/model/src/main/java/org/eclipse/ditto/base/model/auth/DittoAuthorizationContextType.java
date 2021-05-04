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
package org.eclipse.ditto.base.model.auth;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Defines built-in {@link org.eclipse.ditto.base.model.auth.AuthorizationContextType}s which are defined by Ditto.
 *
 * @since 1.1.0
 */
/*
 * This is intentionally not an enum as the enum constants would have difficulties to comply to the
 * hashCode/equals contract when comparing with another subclass of AuthorizationContextType of the same value.
 */
@Immutable
public final class DittoAuthorizationContextType extends AuthorizationContextType {

    /**
     * Type indicating that the authorization context was created the pre-authenticated mechanism via HTTP which is
     * setting an authenticated subject as header field, e.g. in nginx.
     */
    public static final DittoAuthorizationContextType PRE_AUTHENTICATED_HTTP =
            new DittoAuthorizationContextType("pre-authenticated-http");

    /**
     * Type indicating that the authorization context was created using the pre-authenticated mechanism of connections
     * by having configured the contained auth subjects in a Ditto connection source/target.
     */
    public static final DittoAuthorizationContextType PRE_AUTHENTICATED_CONNECTION =
            new DittoAuthorizationContextType("pre-authenticated-connection");

    /**
     * Type indicating that the authorization context was created using a JWT (JSON Web Token).
     */
    public static final DittoAuthorizationContextType JWT = new DittoAuthorizationContextType("jwt");

    /**
     * Type indicating that the authorization context was created from a not specified source, e.g. used in tests and
     * as fallback for deprecated code building authorization contexts without type information.
     */
    public static final DittoAuthorizationContextType UNSPECIFIED = new DittoAuthorizationContextType("unspecified");

    private DittoAuthorizationContextType(final String type) {
        super(type);
    }

    /**
     * Returns an array containing the Ditto specified {@code AuthorizationContextType}s.
     *
     * @return an array containing the Ditto specified authorization context types.
     */
    public static AuthorizationContextType[] values() {
        return new AuthorizationContextType[]{ PRE_AUTHENTICATED_HTTP, PRE_AUTHENTICATED_CONNECTION, JWT, UNSPECIFIED };
    }

    /**
     * Indicates whether the given authorization context type is a Ditto defined one.
     *
     * @param authorizationContextType the authorization context type to be checked.
     * @return {@code true} if the given authorization context type is a constant of DittoAuthorizationContextType.
     */
    public static boolean contains(@Nullable final AuthorizationContextType authorizationContextType) {
        if (null != authorizationContextType) {
            for (final AuthorizationContextType dittoAuthorizationContextType : values()) {
                if (dittoAuthorizationContextType.equals(authorizationContextType)) {
                    return true;
                }
            }
        }
        return false;
    }

}
