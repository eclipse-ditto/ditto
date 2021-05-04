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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;

import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationChain;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationFailureAggregator;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationFailureAggregators;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.gateway.service.security.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ditto's default factory for building authentication directives.
 */
public final class DittoGatewayAuthenticationDirectiveFactory implements GatewayAuthenticationDirectiveFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoGatewayAuthenticationDirectiveFactory.class);

    private final GatewayAuthenticationDirective gatewayHttpAuthenticationDirective;
    private final GatewayAuthenticationDirective gatewayWsAuthenticationDirective;

    public DittoGatewayAuthenticationDirectiveFactory(final AuthenticationConfig authConfig,
            final JwtAuthenticationFactory jwtAuthenticationFactory,
            final Executor authenticationDispatcher) {
        checkNotNull(jwtAuthenticationFactory, "jwtAuthenticationFactory");

        checkNotNull(authConfig, "AuthenticationConfig");
        checkNotNull(authenticationDispatcher, "authentication dispatcher");

        final JwtAuthenticationResultProvider jwtAuthenticationResultProvider =
                jwtAuthenticationFactory.newJwtAuthenticationResultProvider();
        final JwtValidator jwtValidator = jwtAuthenticationFactory.getJwtValidator();

        final JwtAuthenticationProvider jwtHttpAuthenticationProvider =
                JwtAuthenticationProvider.newInstance(jwtAuthenticationResultProvider, jwtValidator);
        final JwtAuthenticationProvider jwtWsAuthenticationProvider =
                JwtAuthenticationProvider.newWsInstance(jwtAuthenticationResultProvider, jwtValidator);

        gatewayHttpAuthenticationDirective =
                generateGatewayAuthenticationDirective(authConfig, jwtHttpAuthenticationProvider,
                        authenticationDispatcher);
        gatewayWsAuthenticationDirective =
                generateGatewayAuthenticationDirective(authConfig, jwtWsAuthenticationProvider,
                        authenticationDispatcher);
    }

    @Override
    public GatewayAuthenticationDirective buildHttpAuthentication() {
        return gatewayHttpAuthenticationDirective;
    }

    @Override
    public GatewayAuthenticationDirective buildWsAuthentication() {
        return gatewayWsAuthenticationDirective;
    }

    private static GatewayAuthenticationDirective generateGatewayAuthenticationDirective(
            final AuthenticationConfig authConfig, final JwtAuthenticationProvider jwtAuthenticationProvider,
            final Executor authenticationDispatcher) {

        final Collection<AuthenticationProvider<?>> authenticationProviders = new ArrayList<>();
        if (authConfig.isPreAuthenticationEnabled()) {
            LOGGER.info("Pre-authentication is enabled!");
            authenticationProviders.add(PreAuthenticatedAuthenticationProvider.getInstance());
        }

        authenticationProviders.add(jwtAuthenticationProvider);

        final AuthenticationFailureAggregator authenticationFailureAggregator =
                AuthenticationFailureAggregators.getDefault();

        final AuthenticationChain authenticationChain =
                AuthenticationChain.getInstance(authenticationProviders, authenticationFailureAggregator,
                        authenticationDispatcher);

        return new GatewayAuthenticationDirective(authenticationChain);
    }

}
