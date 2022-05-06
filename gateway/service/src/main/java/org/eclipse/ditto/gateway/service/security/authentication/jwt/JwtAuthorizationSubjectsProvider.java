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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import akka.actor.ActorSystem;

/**
 * A provider for {@link AuthorizationSubject}s contained in a {@link JsonWebToken}.
 */
public interface JwtAuthorizationSubjectsProvider extends DittoExtensionPoint {


    /**
     * Returns the {@code AuthorizationSubjects} of the given {@code JsonWebToken}.
     *
     * @param jsonWebToken the token containing the authorization subjects.
     * @return the authorization subjects.
     * @throws NullPointerException if {@code jsonWebToken} is {@code null}.
     */
    List<AuthorizationSubject> getAuthorizationSubjects(JsonWebToken jsonWebToken);

    /**
     * Loads the implementation of {@code JwtAuthorizationSubjectsProvider} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code JwtAuthorizationSubjectsProvider} should be loaded.
     * @return the {@code JwtAuthorizationSubjectsProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static JwtAuthorizationSubjectsProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation =
                DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()))
                        .getAuthenticationConfig()
                        .getOAuthConfig()
                        .getJwtAuthorizationSubjectsProvider();
        return new ExtensionId<>(implementation, JwtAuthorizationSubjectsProvider.class).get(actorSystem);
    }

}
