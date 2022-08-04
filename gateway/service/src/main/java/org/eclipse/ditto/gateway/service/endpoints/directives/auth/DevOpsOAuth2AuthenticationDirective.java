/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.gateway.service.util.config.security.DevOpsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.server.AuthorizationFailedRejection;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import scala.util.Try;

/**
 * Custom Akka Http directive performing oauth2 with an {@link #expectedSubjects expected subject}.
 */
public final class DevOpsOAuth2AuthenticationDirective implements DevopsAuthenticationDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevOpsOAuth2AuthenticationDirective.class);

    /**
     * The Http basic auth realm for the "ditto-devops" user used for /devops resource.
     */
    public static final String REALM_DEVOPS = "DITTO-DEVOPS";

    /**
     * The Http basic auth realm for the "ditto-devops" user used for /status resource.
     */
    public static final String REALM_STATUS = "DITTO-STATUS";

    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private final Collection<String> expectedSubjects;

    private DevOpsOAuth2AuthenticationDirective(final JwtAuthenticationProvider jwtAuthenticationProvider,
            final Collection<String> expectedSubjects) {

        this.jwtAuthenticationProvider = checkNotNull(jwtAuthenticationProvider, "jwtAuthenticationProvider");
        this.expectedSubjects = expectedSubjects;
    }

    /**
     * Returns an instance of {@code DevOpsAuthenticationDirective}.
     *
     * @param devOpsConfig the configuration settings of the Gateway service's DevOps endpoint.
     * @param jwtAuthenticationProvider the authentication provider OAuth2 authentication at status resources.
     * @return the instance.
     * @throws NullPointerException if {@code devOpsConfig} is {@code null}.
     */
    public static DevOpsOAuth2AuthenticationDirective status(final DevOpsConfig devOpsConfig,
            final JwtAuthenticationProvider jwtAuthenticationProvider) {

        final Collection<String> expectedSubjects = devOpsConfig.getStatusOAuth2Subjects();
        return new DevOpsOAuth2AuthenticationDirective(jwtAuthenticationProvider, expectedSubjects);
    }

    /**
     * Returns an instance of {@code DevOpsAuthenticationDirective}.
     *
     * @param devOpsConfig the configuration settings of the Gateway service's DevOps endpoint.
     * @param jwtAuthenticationProvider the authentication provider OAuth2 authentication at devops resources.
     * @return the instance.
     * @throws NullPointerException if {@code devOpsConfig} is {@code null}.
     */
    public static DevOpsOAuth2AuthenticationDirective devops(final DevOpsConfig devOpsConfig,
            final JwtAuthenticationProvider jwtAuthenticationProvider) {

        final Collection<String> expectedSubjects = devOpsConfig.getDevopsOAuth2Subjects();
        return new DevOpsOAuth2AuthenticationDirective(jwtAuthenticationProvider, expectedSubjects);
    }

    /**
     * Authenticates the devops resources with the chosen authentication method.
     *
     * @param realm the realm to apply.
     * @param inner the inner route, which will be performed on successful authentication.
     * @return the inner route wrapped with authentication.
     */
    public Route authenticateDevOps(final String realm, final Route inner) {
        LOGGER.debug("DevOps OAuth authentication is enabled for {}.", realm);
        return extractRequestContext(requestContext -> {
            final String authorizationHeaderValue = requestContext.getRequest()
                    .getHeader("authorization")
                    .map(HttpHeader::value)
                    .orElse("");
            LOGGER.debug("Trying to use OAuth2 authentication for authorization header <{}>", authorizationHeaderValue);
            final CompletionStage<AuthenticationResult> authenticationResult =
                    jwtAuthenticationProvider.authenticate(requestContext, DittoHeaders.empty());

            final Function<Try<AuthenticationResult>, Route> handleAuthenticationTry =
                    authenticationResultTry -> handleAuthenticationTry(authenticationResultTry, inner, requestContext);

            return Directives.onComplete(authenticationResult, handleAuthenticationTry);
        });
    }

    private Route handleAuthenticationTry(final Try<AuthenticationResult> authenticationResultTry, final Route inner,
            final RequestContext requestContext) {

        if (authenticationResultTry.isSuccess()) {
            final AuthenticationResult authenticationResult = authenticationResultTry.get();
            if (!authenticationResult.isSuccess()) {
                LOGGER.warn("DevOps Oauth authentication was not successful for request: '{}' because of '{}'.",
                        requestContext.getRequest(), authenticationResult.getReasonOfFailure().getMessage());
                return Directives.failWith(authenticationResult.getReasonOfFailure());
            } else {
                final List<String> authorizationSubjectIds =
                        authenticationResult.getAuthorizationContext().getAuthorizationSubjectIds();
                final boolean isAuthorized = expectedSubjects.isEmpty() || authorizationSubjectIds.stream().anyMatch(expectedSubjects::contains);
                if (isAuthorized) {
                    LOGGER.info("DevOps Oauth authentication was successful.");
                    return inner;
                } else {
                    final String message = String.format(
                            "Unauthorized subject(s): <%s>. Expected: <%s>",
                            authorizationSubjectIds, expectedSubjects
                    );
                    final GatewayAuthenticationFailedException reasonOfFailure =
                            GatewayAuthenticationFailedException.fromMessage(message, DittoHeaders.empty());
                    LOGGER.warn("DevOps Oauth authentication failed.", reasonOfFailure);
                    return Directives.failWith(reasonOfFailure);
                }
            }
        }
        return Directives.reject(AuthorizationFailedRejection.get());
    }

}
