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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link org.eclipse.ditto.base.model.auth.AuthorizationContextType}.
 */
@Immutable
final class ImmutableAuthorizationContextType extends AuthorizationContextType {

    private ImmutableAuthorizationContextType(final String type) {
        super(type);
    }

    /**
     * Returns a new instance of {@code ImmutableAuthorizationContextType} with the given type information.
     *
     * @param type the type information to use for building the context type.
     * @return the new ImmutableAuthorizationContextType.
     * @throws NullPointerException if {@code type} is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty.
     */
    static ImmutableAuthorizationContextType of(final CharSequence type) {
        return new ImmutableAuthorizationContextType(checkNotNull(type, "type").toString());
    }

}
