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

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils.getRequestHeader;

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.dummy.DummyAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.DittoPublicKeyProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.JwtAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.PublicKeyProvider;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.dispatch.MessageDispatcher;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Akka Http directive which performs authentication for the Things service.
 */
public final class GatewayAuthenticationDirective implements AuthenticationDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAuthenticationDirective.class);

    private static final String AUTHORIZATION_JWT = "Bearer";

    private final JwtAuthenticationDirective jwtAuthenticationDirective;
    private final boolean dummyAuthenticationEnabled;

    /**
     * Constructor.
     *
     * @param config the Config of the API Gateway.
     * @param blockingDispatcher a {@link MessageDispatcher} used for blocking calls.
     * @param httpClient the http client to request public keys.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public GatewayAuthenticationDirective(final Config config, final MessageDispatcher blockingDispatcher,
            final HttpClientFacade httpClient) {
        checkNotNull(config, "config");

        final PublicKeyProvider publicKeyProvider = DittoPublicKeyProvider.of(httpClient,
                config.getInt(ConfigKeys.CACHE_PUBLIC_KEYS_MAX),
                config.getDuration(ConfigKeys.CACHE_PUBLIC_KEYS_EXPIRY));

        jwtAuthenticationDirective =
                new JwtAuthenticationDirective(blockingDispatcher, publicKeyProvider);

        dummyAuthenticationEnabled = config.getBoolean(ConfigKeys.AUTHENTICATION_DUMMY_ENABLED);
    }

    /**
     * Depending on the request headers, one of the supported authentication mechanisms is applied.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner route which will be wrapped with the {@link AuthorizationContext}
     * @return the inner route wrapped with the {@link AuthorizationContext}
     */
    @Override
    public Route authenticate(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return extractRequestContext(
                requestContext -> DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId, () -> {
                    final Uri requestUri = requestContext.getRequest().getUri();
                    if (dummyAuthenticationEnabled &&
                            getRequestHeader(requestContext, HttpHeader.X_DITTO_DUMMY_AUTH.getName()).isPresent()) {
                        LOGGER.debug("Applying Dummy Authentication for URI '{}'", requestUri);
                        return dummyAuthentication(correlationId, inner);
                    } else if (authorizedWith(requestContext, AUTHORIZATION_JWT)) {
                        LOGGER.debug("Applying JWT Authentication for URI '{}'", requestUri);
                        return jwtAuthentication(correlationId, inner);
                    } else {
                        LOGGER.debug("Applying missing Authentication for URI '{}'", requestUri);
                        return unauthorized(correlationId, inner);
                    }
                }));
    }

    private boolean authorizedWith(final RequestContext requestContext, final String authorization) {
        final Optional<String> authorizationHeader =
                requestContext.getRequest().getHeader(HttpHeader.AUTHORIZATION.toString().toLowerCase()) //
                        .map(akka.http.javadsl.model.HttpHeader::value) //
                        .filter(headerValue -> headerValue.startsWith(authorization));

        return authorizationHeader.isPresent();
    }

    private Route jwtAuthentication(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return jwtAuthenticationDirective.authenticate(correlationId, inner);
    }

    private Route dummyAuthentication(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return DummyAuthenticationDirective.INSTANCE.authenticate(correlationId, inner);
    }

    private Route unauthorized(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return Directives.complete(StatusCodes.UNAUTHORIZED);
    }
}
