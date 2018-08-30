/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.dummy.DummyAuthenticationProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.DittoAuthorizationSubjectsProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.DittoPublicKeyProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.JwtAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.JwtSubjectIssuerConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.JwtSubjectIssuersConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.PublicKeyProvider;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Ditto's default factory for building authentication directives.
 */
public class DittoGatewayAuthenticationDirectiveFactory implements GatewayAuthenticationDirectiveFactory {

    private static final String JWT_ISSUER_GOOGLE_DOMAIN = "accounts.google.com";
    private static final String JWT_ISSUER_GOOGLE_URL = "https://accounts.google.com";
    private static final String JWK_RESOURCE_GOOGLE = "https://www.googleapis.com/oauth2/v2/certs";

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoGatewayAuthenticationDirectiveFactory.class);

    private final GatewayAuthenticationDirective gatewayAuthenticationDirective;

    public DittoGatewayAuthenticationDirectiveFactory(final Config config,
            final HttpClientFacade httpClient) {
        requireNonNull(config);
        requireNonNull(httpClient);

        gatewayAuthenticationDirective = generateGatewayAuthenticationDirective(config, httpClient);
    }

    @Override
    public GatewayAuthenticationDirective buildRestApiAuthentication() {
        return gatewayAuthenticationDirective;
    }

    @Override
    public GatewayAuthenticationDirective buildWsAuthentication() {
        return gatewayAuthenticationDirective;
    }

    private static GatewayAuthenticationDirective generateGatewayAuthenticationDirective(final Config config,
            final HttpClientFacade httpClient) {
        final boolean dummyAuthEnabled = config.getBoolean(ConfigKeys.AUTHENTICATION_DUMMY_ENABLED);

        final List<AuthenticationProvider> authenticationChain = new LinkedList<>();
        if (dummyAuthEnabled) {
            LOGGER.warn("Dummy authentication is enabled - Do not use this feature in production.");
            authenticationChain.add(DummyAuthenticationProvider.INSTANCE);
        }


        final JwtSubjectIssuersConfig jwtSubjectIssuersConfig = buildJwtSubjectIssuersConfig();

        final PublicKeyProvider publicKeyProvider = DittoPublicKeyProvider.of(jwtSubjectIssuersConfig, httpClient,
                config.getInt(ConfigKeys.CACHE_PUBLIC_KEYS_MAX),
                config.getDuration(ConfigKeys.CACHE_PUBLIC_KEYS_EXPIRY), "ditto_authorization_jwt_publicKeys_cache");
        final DittoAuthorizationSubjectsProvider authorizationSubjectsProvider =
                DittoAuthorizationSubjectsProvider.of(jwtSubjectIssuersConfig);

        authenticationChain.add(
                new JwtAuthenticationDirective(publicKeyProvider, authorizationSubjectsProvider));

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
