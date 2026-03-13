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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationChain;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationFailureAggregator;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationFailureAggregators;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Ditto's default factory for building authentication directives.
 */
public final class DittoGatewayAuthenticationDirectiveFactory implements GatewayAuthenticationDirectiveFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoGatewayAuthenticationDirectiveFactory.class);
    private static final String AUTHENTICATION_DISPATCHER_NAME = "authentication-dispatcher";

    private volatile AuthenticationConfig authConfig;
    private final Executor authenticationDispatcher;
    private final Config dittoExtensionConfig;
    @Nullable private GatewayAuthenticationDirective gatewayHttpAuthenticationDirective;
    @Nullable private GatewayAuthenticationDirective gatewayWsAuthenticationDirective;
    @Nullable private JwtAuthenticationProvider httpJwtProvider;
    @Nullable private JwtAuthenticationProvider wsJwtProvider;

    public DittoGatewayAuthenticationDirectiveFactory(final ActorSystem actorSystem, final Config config) {
        authConfig = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()))
                .getAuthenticationConfig();
        authenticationDispatcher = actorSystem.dispatchers().lookup(AUTHENTICATION_DISPATCHER_NAME);
        dittoExtensionConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
    }

    @Override
    public GatewayAuthenticationDirective buildHttpAuthentication(
            final JwtAuthenticationFactory jwtAuthenticationFactory) {

        if (null == gatewayHttpAuthenticationDirective) {
            httpJwtProvider = JwtAuthenticationProvider.newInstance(
                    jwtAuthenticationFactory.newJwtAuthenticationResultProvider(
                            dittoExtensionConfig, null
                    ),
                    jwtAuthenticationFactory.getJwtValidator()
            );
            gatewayHttpAuthenticationDirective =
                    generateGatewayAuthenticationDirective(authConfig, httpJwtProvider,
                            authenticationDispatcher);
        }
        return gatewayHttpAuthenticationDirective;
    }

    @Override
    public GatewayAuthenticationDirective buildWsAuthentication(
            final JwtAuthenticationFactory jwtAuthenticationFactory) {

        if (null == gatewayWsAuthenticationDirective) {
            wsJwtProvider = JwtAuthenticationProvider.newWsInstance(
                    jwtAuthenticationFactory.newJwtAuthenticationResultProvider(
                            dittoExtensionConfig, null
                    ),
                    jwtAuthenticationFactory.getJwtValidator()
            );
            gatewayWsAuthenticationDirective =
                    generateGatewayAuthenticationDirective(authConfig, wsJwtProvider,
                            authenticationDispatcher);
        }
        return gatewayWsAuthenticationDirective;
    }

    /**
     * Updates the authentication configuration. Called when dynamic config changes are detected.
     * Rebuilds the authentication chains for both HTTP and WebSocket directives if they have been built.
     *
     * @param authenticationConfig the new authentication config.
     */
    public void updateAuthConfig(final AuthenticationConfig authenticationConfig) {
        final boolean preAuthChanged = this.authConfig.isPreAuthenticationEnabled() !=
                authenticationConfig.isPreAuthenticationEnabled();
        this.authConfig = authenticationConfig;

        if (preAuthChanged) {
            LOGGER.info("Pre-authentication enabled changed to <{}>. Rebuilding authentication chains.",
                    authenticationConfig.isPreAuthenticationEnabled());
            if (gatewayHttpAuthenticationDirective != null && httpJwtProvider != null) {
                gatewayHttpAuthenticationDirective.updateAuthenticationChain(
                        buildAuthenticationChain(authenticationConfig, httpJwtProvider, authenticationDispatcher));
            }
            if (gatewayWsAuthenticationDirective != null && wsJwtProvider != null) {
                gatewayWsAuthenticationDirective.updateAuthenticationChain(
                        buildAuthenticationChain(authenticationConfig, wsJwtProvider, authenticationDispatcher));
            }
        }
    }

    private static AuthenticationChain buildAuthenticationChain(
            final AuthenticationConfig authConfig,
            final AuthenticationProvider<AuthenticationResult> jwtAuthenticationProvider,
            final Executor authenticationDispatcher) {

        final Collection<AuthenticationProvider<?>> authenticationProviders = new ArrayList<>();
        if (authConfig.isPreAuthenticationEnabled()) {
            LOGGER.info("Pre-authentication is enabled!");
            authenticationProviders.add(PreAuthenticatedAuthenticationProvider.getInstance());
        }

        authenticationProviders.add(jwtAuthenticationProvider);

        final AuthenticationFailureAggregator authenticationFailureAggregator =
                AuthenticationFailureAggregators.getDefault();

        return AuthenticationChain.getInstance(authenticationProviders, authenticationFailureAggregator,
                authenticationDispatcher);
    }

    private static GatewayAuthenticationDirective generateGatewayAuthenticationDirective(
            final AuthenticationConfig authConfig,
            final AuthenticationProvider<AuthenticationResult> jwtAuthenticationProvider,
            final Executor authenticationDispatcher) {

        return new GatewayAuthenticationDirective(
                buildAuthenticationChain(authConfig, jwtAuthenticationProvider, authenticationDispatcher));
    }

}
