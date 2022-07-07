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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Default implementation of {@link JwtAuthenticationResultProvider}.
 */
@Immutable
public final class DefaultJwtAuthenticationResultProvider implements JwtAuthenticationResultProvider {

    private final JwtAuthorizationSubjectsProvider authSubjectsProvider;

    public DefaultJwtAuthenticationResultProvider(final ActorSystem actorSystem, final Config extensionConfig) {
        authSubjectsProvider = JwtAuthorizationSubjectsProvider.get(actorSystem, extensionConfig);
    }

    @Override
    public CompletionStage<JwtAuthenticationResult> getAuthenticationResult(final JsonWebToken jwt,
            final DittoHeaders dittoHeaders) {

        final List<AuthorizationSubject> authSubjects = authSubjectsProvider.getAuthorizationSubjects(jwt);
        return CompletableFuture.completedStage(JwtAuthenticationResult.successful(dittoHeaders,
                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.JWT, authSubjects),
                jwt));
    }

}
