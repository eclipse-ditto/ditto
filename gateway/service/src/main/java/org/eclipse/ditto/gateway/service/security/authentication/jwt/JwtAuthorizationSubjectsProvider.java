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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import java.util.List;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.jwt.model.JsonWebToken;

/**
 * A provider for {@link AuthorizationSubject}s contained in a {@link JsonWebToken}.
 */
public interface JwtAuthorizationSubjectsProvider {

    /**
     * Returns the {@code AuthorizationSubjects} of the given {@code JsonWebToken}.
     *
     * @param jsonWebToken the token containing the authorization subjects.
     * @return the authorization subjects.
     * @throws NullPointerException if {@code jsonWebToken} is {@code null}.
     */
    List<AuthorizationSubject> getAuthorizationSubjects(JsonWebToken jsonWebToken);

}
