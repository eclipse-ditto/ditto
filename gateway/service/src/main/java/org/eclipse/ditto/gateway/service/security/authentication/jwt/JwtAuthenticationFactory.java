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

import javax.annotation.Nullable;

import org.eclipse.ditto.gateway.service.security.utils.HttpClientFacade;
import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;

/**
 * A factory for {@link org.eclipse.ditto.jwt.model.JsonWebToken} related security.
 */
public final class JwtAuthenticationFactory {

    private static final String PUBLIC_KEY_CACHE_NAME = "ditto_authorization_jwt_publicKeys_cache";

    private final OAuthConfig oAuthConfig;
    private final CacheConfig publicKeyCacheConfig;
    private final HttpClientFacade httpClientFacade;
    private final JwtAuthorizationSubjectsProviderFactory jwtAuthorizationSubjectsProviderFactory;

    @Nullable private JwtValidator jwtValidator;
    @Nullable private JwtSubjectIssuersConfig jwtSubjectIssuersConfig;
    @Nullable private PublicKeyProvider publicKeyProvider;

    private JwtAuthenticationFactory(final OAuthConfig oAuthConfig,
            final CacheConfig publicKeyCacheConfig,
            final HttpClientFacade httpClientFacade,
            final JwtAuthorizationSubjectsProviderFactory jwtAuthorizationSubjectsProviderFactory) {

        this.oAuthConfig = checkNotNull(oAuthConfig, "authenticationConfig");
        this.publicKeyCacheConfig = checkNotNull(publicKeyCacheConfig, "publicKeyCacheConfig");
        this.httpClientFacade = checkNotNull(httpClientFacade, "httpClientFacade");
        this.jwtAuthorizationSubjectsProviderFactory =
                checkNotNull(jwtAuthorizationSubjectsProviderFactory, "jwtAuthorizationSubjectsProviderFactory");
    }

    /**
     * Creates a new {@code JwtAuthenticationFactory} instance.
     *
     * @param oAuthConfig the OAuth configuration.
     * @param publicKeyCacheConfig the public key cache configuration.
     * @param httpClientFacade the client facade of the HTTP client.
     * @param jwtAuthorizationSubjectsProviderFactory used to instantiate a new auth subjects provider.
     * @return the new created instance.
     */
    public static JwtAuthenticationFactory newInstance(final OAuthConfig oAuthConfig,
            final CacheConfig publicKeyCacheConfig,
            final HttpClientFacade httpClientFacade,
            final JwtAuthorizationSubjectsProviderFactory jwtAuthorizationSubjectsProviderFactory) {

        return new JwtAuthenticationFactory(oAuthConfig, publicKeyCacheConfig, httpClientFacade,
                jwtAuthorizationSubjectsProviderFactory);
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
                    PUBLIC_KEY_CACHE_NAME,
                    oAuthConfig);
        }

        return publicKeyProvider;
    }

    private JwtSubjectIssuersConfig getJwtSubjectIssuersConfig() {
        if (null == jwtSubjectIssuersConfig) {
            jwtSubjectIssuersConfig = JwtSubjectIssuersConfig.fromOAuthConfig(oAuthConfig);
        }
        return jwtSubjectIssuersConfig;
    }

    public JwtAuthenticationResultProvider newJwtAuthenticationResultProvider() {
        final var authorizationSubjectsProvider =
               jwtAuthorizationSubjectsProviderFactory.newProvider(getJwtSubjectIssuersConfig());

        return DefaultJwtAuthenticationResultProvider.of(authorizationSubjectsProvider);
    }

}
