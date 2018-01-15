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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

import java.util.List;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.services.gateway.security.jwt.JsonWebToken;

/**
 * A provider for {@link org.eclipse.ditto.model.base.auth.AuthorizationSubject}s contained in a
 * {@link org.eclipse.ditto.services.gateway.security.jwt.JsonWebToken}.
 */
public interface AuthorizationSubjectsProvider {

    /**
     * Returns the {@code AuthorizationSubjects} of the given {@code JsonWebToken}.
     *
     * @param jsonWebToken the token containing the authorization subjects.
     * @return the authorization subjects.
     * @throws java.lang.NullPointerException if {@code jsonWebToken} is {@code null}.
     */
    List<AuthorizationSubject> getAuthorizationSubjects(final JsonWebToken jsonWebToken);

}
