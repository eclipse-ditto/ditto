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
import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import akka.actor.ActorSystem;

/**
 * A provider for {@link AuthorizationSubject}s contained in a {@link JsonWebToken}.
 */
public abstract class JwtAuthorizationSubjectsProvider extends DittoExtensionPoint {


    protected JwtAuthorizationSubjectsProvider(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    /**
     * Returns the {@code AuthorizationSubjects} of the given {@code JsonWebToken}.
     *
     * @param jsonWebToken the token containing the authorization subjects.
     * @return the authorization subjects.
     * @throws NullPointerException if {@code jsonWebToken} is {@code null}.
     */
    public abstract List<AuthorizationSubject> getAuthorizationSubjects(JsonWebToken jsonWebToken);

    /**
     * Loads the implementation of {@code JwtAuthorizationSubjectsProvider} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code JwtAuthorizationSubjectsProvider} should be loaded.
     * @return the {@code JwtAuthorizationSubjectsProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    public static JwtAuthorizationSubjectsProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation =
                getOAuthConfig(actorSystem).getJwtAuthorizationSubjectsProvider();
        return new ExtensionId<>(implementation, JwtAuthorizationSubjectsProvider.class).get(actorSystem);
    }

    protected static OAuthConfig getOAuthConfig(final ActorSystem actorSystem) {
        return DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                        actorSystem.settings().config())).getAuthenticationConfig()
                .getOAuthConfig();
    }

}
