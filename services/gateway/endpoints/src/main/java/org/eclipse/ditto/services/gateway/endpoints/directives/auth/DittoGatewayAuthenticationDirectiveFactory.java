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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;

import org.eclipse.ditto.services.gateway.endpoints.config.AuthenticationConfig;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationChain;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationFailureAggregator;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationFailureAggregators;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.authentication.dummy.DummyAuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DefaultJwtAuthorizationContextProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DittoJwtAuthorizationSubjectsProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtSubjectIssuersConfig;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ditto's default factory for building authentication directives.
 */
public final class DittoGatewayAuthenticationDirectiveFactory implements GatewayAuthenticationDirectiveFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoGatewayAuthenticationDirectiveFactory.class);

    private final GatewayAuthenticationDirective gatewayAuthenticationDirective;

    public DittoGatewayAuthenticationDirectiveFactory(final AuthenticationConfig authConfig,
            final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final JwtValidator jwtValidator,
            final Executor authenticationDispatcher) {

        checkNotNull(authConfig, "AuthenticationConfig");
        checkNotNull(jwtSubjectIssuersConfig, "jwtSubjectIssuerConfig");
        checkNotNull(authenticationDispatcher, "authentication dispatcher");

        gatewayAuthenticationDirective = generateGatewayAuthenticationDirective(authConfig,
                jwtSubjectIssuersConfig, jwtValidator, authenticationDispatcher);
    }

    @Override
    public GatewayAuthenticationDirective buildHttpAuthentication() {
        return gatewayAuthenticationDirective;
    }

    @Override
    public GatewayAuthenticationDirective buildWsAuthentication() {
        return gatewayAuthenticationDirective;
    }

    private static GatewayAuthenticationDirective generateGatewayAuthenticationDirective(
            final AuthenticationConfig authConfig,
            final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final JwtValidator jwtValidator,
            final Executor authenticationDispatcher) {

        final Collection<AuthenticationProvider> authenticationProviders = new ArrayList<>();
        if (authConfig.isDummyAuthenticationEnabled()) {
            LOGGER.warn("Dummy authentication is enabled - Do not use this feature in production.");
            authenticationProviders.add(DummyAuthenticationProvider.getInstance());
        }

        final DittoJwtAuthorizationSubjectsProvider authorizationSubjectsProvider =
                DittoJwtAuthorizationSubjectsProvider.of(jwtSubjectIssuersConfig);
        final DefaultJwtAuthorizationContextProvider authorizationContextProvider =
                DefaultJwtAuthorizationContextProvider.getInstance(authorizationSubjectsProvider);
        final JwtAuthenticationProvider jwtAuthenticationProvider =
                JwtAuthenticationProvider.getInstance(jwtValidator, authorizationContextProvider);

        authenticationProviders.add(jwtAuthenticationProvider);

        final AuthenticationFailureAggregator authenticationFailureAggregator =
                AuthenticationFailureAggregators.getDefault();

        final AuthenticationChain authenticationChain =
                AuthenticationChain.getInstance(authenticationProviders, authenticationFailureAggregator,
                        authenticationDispatcher);

        return new GatewayAuthenticationDirective(authenticationChain);
    }

}
