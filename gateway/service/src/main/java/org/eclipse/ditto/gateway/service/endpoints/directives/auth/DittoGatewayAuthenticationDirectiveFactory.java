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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationChain;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationFailureAggregator;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationFailureAggregators;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.http.javadsl.server.RequestContext;

/**
 * Ditto's default factory for building authentication directives.
 */
public final class DittoGatewayAuthenticationDirectiveFactory implements GatewayAuthenticationDirectiveFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoGatewayAuthenticationDirectiveFactory.class);
    private static final String AUTHENTICATION_DISPATCHER_NAME = "authentication-dispatcher";

    private final AuthenticationConfig authConfig;
    private final Executor authenticationDispatcher;
    @Nullable private GatewayAuthenticationDirective gatewayHttpAuthenticationDirective;
    @Nullable private GatewayAuthenticationDirective gatewayWsAuthenticationDirective;
    @Nullable private GatewayAuthenticationDirective gatewayCoapAuthenticationDirective;

    public DittoGatewayAuthenticationDirectiveFactory(final ActorSystem actorSystem, final Config config) {
        authConfig = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()))
                .getAuthenticationConfig();
        authenticationDispatcher = actorSystem.dispatchers().lookup(AUTHENTICATION_DISPATCHER_NAME);
    }

    @Override
    public GatewayAuthenticationDirective buildHttpAuthentication(
            final JwtAuthenticationFactory jwtAuthenticationFactory) {

        if (null == gatewayHttpAuthenticationDirective) {
            final JwtAuthenticationProvider jwtHttpAuthenticationProvider =
                    JwtAuthenticationProvider.newInstance(jwtAuthenticationFactory.newJwtAuthenticationResultProvider(
                                    "ditto.gateway.authentication.oauth"
                            ),
                            jwtAuthenticationFactory.getJwtValidator());
            gatewayHttpAuthenticationDirective =
                    generateGatewayAuthenticationDirective(authConfig, jwtHttpAuthenticationProvider,
                            authenticationDispatcher);
        }
        return gatewayHttpAuthenticationDirective;
    }

    @Override
    public GatewayAuthenticationDirective buildWsAuthentication(
            final JwtAuthenticationFactory jwtAuthenticationFactory) {

        if (null == gatewayWsAuthenticationDirective) {
            final JwtAuthenticationProvider jwtWsAuthenticationProvider =
                    JwtAuthenticationProvider.newWsInstance(
                            jwtAuthenticationFactory.newJwtAuthenticationResultProvider(
                                    "ditto.gateway.authentication.oauth"
                            ),
                            jwtAuthenticationFactory.getJwtValidator());
            gatewayWsAuthenticationDirective =
                    generateGatewayAuthenticationDirective(authConfig, jwtWsAuthenticationProvider,
                            authenticationDispatcher);
        }
        return gatewayWsAuthenticationDirective;
    }

    @Override
    public GatewayAuthenticationDirective buildCoapAuthentication() {

        if (null == gatewayCoapAuthenticationDirective) {
            final AuthenticationProvider<AuthenticationResult> coapAuthenticationProvider = new AuthenticationProvider<>() {
                        @Override
                        public boolean isApplicable(final RequestContext requestContext) {
                            return true; // TODO TJ extract CoAP AuthenticationProvider
                        }

                        @Override
                        public CompletableFuture<AuthenticationResult> authenticate(final RequestContext requestContext,
                                final DittoHeaders dittoHeaders) {
                            return CompletableFuture.completedFuture(
                                    DefaultAuthenticationResult.successful(dittoHeaders,
                                            AuthorizationContext.newInstance(
                                                    DittoAuthorizationContextType.PRE_AUTHENTICATED_COAP,
                                                    AuthorizationSubject.newInstance("coap:some-device-id") // TODO TJ determine from coap auth
                                            )
                                    )
                            );
                        }
                    };
            gatewayCoapAuthenticationDirective =
                    generateGatewayAuthenticationDirective(authConfig, coapAuthenticationProvider,
                            authenticationDispatcher);
        }
        return gatewayCoapAuthenticationDirective;
    }

    private static GatewayAuthenticationDirective generateGatewayAuthenticationDirective(
            final AuthenticationConfig authConfig,
            @Nullable final AuthenticationProvider<AuthenticationResult> authenticationProvider,
            final Executor authenticationDispatcher) {

        final Collection<AuthenticationProvider<?>> authenticationProviders = new ArrayList<>();
        if (authConfig.isPreAuthenticationEnabled()) {
            LOGGER.info("Pre-authentication is enabled!");
            authenticationProviders.add(PreAuthenticatedAuthenticationProvider.getInstance());
        }

        if (null != authenticationProvider) {
            authenticationProviders.add(authenticationProvider);
        }

        final AuthenticationFailureAggregator authenticationFailureAggregator =
                AuthenticationFailureAggregators.getDefault();

        final AuthenticationChain authenticationChain =
                AuthenticationChain.getInstance(authenticationProviders, authenticationFailureAggregator,
                        authenticationDispatcher);

        return new GatewayAuthenticationDirective(authenticationChain);
    }

}
