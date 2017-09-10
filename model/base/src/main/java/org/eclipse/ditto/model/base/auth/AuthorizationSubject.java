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
