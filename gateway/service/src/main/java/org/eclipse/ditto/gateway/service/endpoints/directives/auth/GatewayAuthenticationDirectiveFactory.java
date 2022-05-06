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
package org.eclipse.ditto.gateway.service.endpoints.directives.auth;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;

/**
 * Factory for authentication directives.
 */
public interface GatewayAuthenticationDirectiveFactory extends DittoExtensionPoint {

    /**
     * Builds the {@link GatewayAuthenticationDirective authentication directive} that should be used for HTTP API.
     *
     * @return The built {@link GatewayAuthenticationDirective authentication directive}.
     */
    GatewayAuthenticationDirective buildHttpAuthentication(JwtAuthenticationFactory jwtAuthenticationFactory);

    /**
     * Builds the {@link GatewayAuthenticationDirective authentication directive} that should be used for WebSocket API.
     *
     * @return The built {@link GatewayAuthenticationDirective authentication directive}.
     */
    GatewayAuthenticationDirective buildWsAuthentication(JwtAuthenticationFactory jwtAuthenticationFactory);

    /**
     * Loads the implementation of {@code GatewayAuthenticationDirectiveFactory} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code GatewayAuthenticationDirectiveFactory} should be loaded.
     * @return the {@code GatewayAuthenticationDirectiveFactory} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static GatewayAuthenticationDirectiveFactory get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final AuthenticationConfig authenticationConfig =
                DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()))
                        .getAuthenticationConfig();
        final var implementation = authenticationConfig.getGatewayAuthenticationDirectiveFactory();
        return new ExtensionId<>(implementation, GatewayAuthenticationDirectiveFactory.class).get(actorSystem);
    }

}
