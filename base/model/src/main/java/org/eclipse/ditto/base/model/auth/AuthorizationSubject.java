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

import javax.annotation.concurrent.Immutable;

/**
 * An {@code AuthorizationSubject} represents an entity which is subject of authorization at Ditto. For
 * example, a user name can be an authorization subject.
 */
@Immutable
public interface AuthorizationSubject {

    /**
     * Returns a new immutable {@code AuthorizationSubject} with the given identifier.
     *
     * @param identifier the identifier of the new authorization subject.
     * @return the new {@code AuthorizationSubject}.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     * @throws IllegalArgumentException if {@code identifier} is empty
     */
    static AuthorizationSubject newInstance(final CharSequence identifier) {
        return AuthorizationModelFactory.newAuthSubject(identifier);
    }

    /**
     * Returns the identifier of this authorization subject.
     *
     * @return the identifier of this authorization subject.
     */
    String getId();

}
