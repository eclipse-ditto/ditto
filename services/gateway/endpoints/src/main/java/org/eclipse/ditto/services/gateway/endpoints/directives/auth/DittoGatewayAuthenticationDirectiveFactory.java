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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationChain;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationFailureAggregator;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationFailureAggregators;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.authentication.dummy.DummyAuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DefaultJwtAuthorizationContextProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DittoJwtAuthorizationSubjectsProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DittoPublicKeyProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtSubjectIssuerConfig;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtSubjectIssuersConfig;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.PublicKeyProvider;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Ditto's default factory for building authentication directives.
 */
public final class DittoGatewayAuthenticationDirectiveFactory implements GatewayAuthenticationDirectiveFactory {

    private static final String JWT_ISSUER_GOOGLE_DOMAIN = "accounts.google.com";
    private static final String JWT_ISSUER_GOOGLE_URL = "https://accounts.google.com";
    private static final String JWK_RESOURCE_GOOGLE = "https://www.googleapis.com/oauth2/v2/certs";

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoGatewayAuthenticationDirectiveFactory.class);

    private final GatewayAuthenticationDirective gatewayAuthenticationDirective;

    public DittoGatewayAuthenticationDirectiveFactory(final Config config, final HttpClientFacade httpClient,
            final Executor blockingDispatcher) {

        checkNotNull(config, "Config");
        checkNotNull(httpClient, "HTTP client");
        checkNotNull(blockingDispatcher, "blocking dispatcher");

        gatewayAuthenticationDirective = generateGatewayAuthenticationDirective(config, httpClient, blockingDispatcher);
    }

    @Override
    public GatewayAuthenticationDirective buildHttpAuthentication() {
        return gatewayAuthenticationDirective;
    }

    @Override
    public GatewayAuthenticationDirective buildWsAuthentication() {
        return gatewayAuthenticationDirective;
    }

    private static GatewayAuthenticationDirective generateGatewayAuthenticationDirective(final Config config,
            final HttpClientFacade httpClient, final Executor blockingDispatcher) {

        final boolean dummyAuthEnabled = config.getBoolean(ConfigKeys.AUTHENTICATION_DUMMY_ENABLED);

        final Collection<AuthenticationProvider> authenticationProviders = new ArrayList<>();
        if (dummyAuthEnabled) {
            LOGGER.warn("Dummy authentication is enabled - Do not use this feature in production.");
            authenticationProviders.add(DummyAuthenticationProvider.getInstance());
        }

        final JwtSubjectIssuersConfig jwtSubjectIssuersConfig = buildJwtSubjectIssuersConfig();

        final PublicKeyProvider publicKeyProvider = DittoPublicKeyProvider.of(jwtSubjectIssuersConfig, httpClient,
                config.getInt(ConfigKeys.CACHE_PUBLIC_KEYS_MAX),
                config.getDuration(ConfigKeys.CACHE_PUBLIC_KEYS_EXPIRY), "ditto_authorization_jwt_publicKeys_cache");
        final DittoJwtAuthorizationSubjectsProvider authorizationSubjectsProvider =
                DittoJwtAuthorizationSubjectsProvider.of(jwtSubjectIssuersConfig);
        final DefaultJwtAuthorizationContextProvider authorizationContextProvider =
                DefaultJwtAuthorizationContextProvider.getInstance(authorizationSubjectsProvider);
        final JwtAuthenticationProvider jwtAuthenticationProvider =
                JwtAuthenticationProvider.getInstance(publicKeyProvider, authorizationContextProvider);

        authenticationProviders.add(jwtAuthenticationProvider);

        final AuthenticationFailureAggregator authenticationFailureAggregator =
                AuthenticationFailureAggregators.getDefault();

        final AuthenticationChain authenticationChain =
                AuthenticationChain.getInstance(authenticationProviders, authenticationFailureAggregator,
                        blockingDispatcher);

        return new GatewayAuthenticationDirective(authenticationChain);
    }

    private static JwtSubjectIssuersConfig buildJwtSubjectIssuersConfig() {
        final Set<JwtSubjectIssuerConfig> configItems = new HashSet<>();

        configItems.add(new JwtSubjectIssuerConfig(SubjectIssuer.GOOGLE, JWT_ISSUER_GOOGLE_DOMAIN,
                JWK_RESOURCE_GOOGLE));
        configItems.add(new JwtSubjectIssuerConfig(SubjectIssuer.GOOGLE, JWT_ISSUER_GOOGLE_URL,
                JWK_RESOURCE_GOOGLE));

        return new JwtSubjectIssuersConfig(configItems);
    }

}
