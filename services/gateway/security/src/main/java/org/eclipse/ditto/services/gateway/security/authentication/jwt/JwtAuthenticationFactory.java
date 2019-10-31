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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.gateway.security.config.OAuthConfig;
import org.eclipse.ditto.services.gateway.security.utils.HttpClientFacade;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;

/**
 * A factory for {@link org.eclipse.ditto.model.jwt.JsonWebToken} related security.
 */
public final class JwtAuthenticationFactory {

    private static final String PUBLIC_KEY_CACHE_NAME = "ditto_authorization_jwt_publicKeys_cache";

    private final OAuthConfig oAuthConfig;
    private final CacheConfig publicKeyCacheConfig;
    private final HttpClientFacade httpClientFacade;

    @Nullable private JwtValidator jwtValidator;
    @Nullable private JwtSubjectIssuersConfig jwtSubjectIssuersConfig;
    @Nullable private PublicKeyProvider publicKeyProvider;

    private JwtAuthenticationFactory(final OAuthConfig oAuthConfig,
            final CacheConfig publicKeyCacheConfig,
            final HttpClientFacade httpClientFacade) {
        this.oAuthConfig = checkNotNull(oAuthConfig, "authenticationConfig");
        this.publicKeyCacheConfig = checkNotNull(publicKeyCacheConfig, "publicKeyCacheConfig");
        this.httpClientFacade = checkNotNull(httpClientFacade, "httpClientFacade");
    }

    /**
     * Creates a new {@code JwtAuthenticationFactory} instance.
     */
    public static JwtAuthenticationFactory newInstance(final OAuthConfig oAuthConfig,
            final CacheConfig publicKeyCacheConfig,
            final HttpClientFacade httpClientFacade) {
        return new JwtAuthenticationFactory(oAuthConfig, publicKeyCacheConfig, httpClientFacade);
    }

    public JwtValidator getJwtValidator() {
        if (null == jwtValidator) {
            jwtValidator = DefaultJwtValidator.of(getPublicKeyProvider());
        }
        return jwtValidator;
    }

    private PublicKeyProvider getPublicKeyProvider() {
        if (null == publicKeyProvider) {
            publicKeyProvider = DittoPublicKeyProvider.of(
                    getJwtSubjectIssuersConfig(),
                    httpClientFacade,
                    publicKeyCacheConfig,
                    PUBLIC_KEY_CACHE_NAME);
        }

        return publicKeyProvider;
    }

    private JwtSubjectIssuersConfig getJwtSubjectIssuersConfig() {
        if (null == jwtSubjectIssuersConfig) {
            jwtSubjectIssuersConfig = buildJwtSubjectIssuersConfig(oAuthConfig);
        }
        return jwtSubjectIssuersConfig;
    }

    private static JwtSubjectIssuersConfig buildJwtSubjectIssuersConfig(final OAuthConfig config) {
        final Set<JwtSubjectIssuerConfig> configItems = config.getOpenIdConnectIssuers().entrySet().stream()
                .map(entry -> new JwtSubjectIssuerConfig(entry.getValue(), entry.getKey()))
                .collect(Collectors.toSet());

        return new JwtSubjectIssuersConfig(configItems);
    }

    public JwtAuthorizationContextProvider newJwtAuthorizationContextProvider() {
        final DittoJwtAuthorizationSubjectsProvider authorizationSubjectsProvider =
                DittoJwtAuthorizationSubjectsProvider.of(getJwtSubjectIssuersConfig());

        return DefaultJwtAuthorizationContextProvider.of(authorizationSubjectsProvider);
    }

}
