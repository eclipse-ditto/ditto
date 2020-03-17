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
package org.eclipse.ditto.services.gateway.security.authentication.preauth;

import static org.eclipse.ditto.services.gateway.security.utils.HttpUtils.getRequestHeader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationContextType;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.TimeMeasuringAuthenticationProvider;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;

import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by using a defined header field {@link HttpHeader#X_DITTO_PRE_AUTH} which proxies in front
 * of Ditto may set to inject authenticated subjects into a HTTP request.
 * <p>
 * Enabled/disabled by {@link org.eclipse.ditto.services.gateway.security.config.AuthenticationConfig#isPreAuthenticatedAuthenticationEnabled()}.
 * </p>
 * If this is enabled it is of upmost importance that only the proxy in front of Ditto sets the defined header field
 * {@link HttpHeader#X_DITTO_PRE_AUTH}, otherwise anyone using the HTTP API may impersonate any other authorization
 * subject.
 *
 * @since 1.1.0
 */
@Immutable
public final class PreAuthenticatedAuthenticationProvider
        extends TimeMeasuringAuthenticationProvider<AuthenticationResult> {

    private static final DittoLogger LOGGER =
            DittoLoggerFactory.getLogger(PreAuthenticatedAuthenticationProvider.class);

    private static final PreAuthenticatedAuthenticationProvider INSTANCE = new PreAuthenticatedAuthenticationProvider();

    private PreAuthenticatedAuthenticationProvider() {
        super();
    }

    /**
     * Creates a new instance of the JWT authentication provider.
     *
     * @return the created instance.
     */
    public static PreAuthenticatedAuthenticationProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isApplicable(final RequestContext requestContext) {
        return containsHeader(requestContext, HttpHeader.X_DITTO_PRE_AUTH) ||
                containsHeader(requestContext, HttpHeader.X_DITTO_DUMMY_AUTH);
    }

    protected boolean containsHeader(final RequestContext requestContext, final HttpHeader header) {
        return getRequestHeader(requestContext, header.getName()).isPresent() ||
                getRequestParam(requestContext, header).isPresent();
    }

    @Override
    protected AuthenticationResult tryToAuthenticate(final RequestContext requestContext,
            final DittoHeaders dittoHeaders) {

        final Optional<String> preAuthOpt = getPreAuthenticated(requestContext);

        if (preAuthOpt.isEmpty()) {
            return DefaultAuthenticationResult.failed(dittoHeaders, buildNotApplicableException(dittoHeaders));
        }

        final String preAuthenticatedSubject = preAuthOpt.get();

        final List<AuthorizationSubject> authorizationSubjects = extractAuthorizationSubjects(preAuthenticatedSubject);
        if (authorizationSubjects.isEmpty()) {
            return toFailedAuthenticationResult(
                    buildFailedToExtractAuthorizationSubjectsException(preAuthenticatedSubject, dittoHeaders),
                    dittoHeaders);
        }

        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP, authorizationSubjects);

        LOGGER.withCorrelationId(dittoHeaders)
                .info("Pre-authentication has been applied resulting in the following AuthorizationContext: {}",
                        authorizationContext);

        return DefaultAuthenticationResult.successful(dittoHeaders, authorizationContext);
    }

    @Override
    protected AuthenticationResult toFailedAuthenticationResult(final Throwable throwable,
            final DittoHeaders dittoHeaders) {

        return DefaultAuthenticationResult.failed(dittoHeaders, toDittoRuntimeException(throwable, dittoHeaders));
    }

    @Override
    public AuthorizationContextType getType(final RequestContext requestContext) {
        return DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP;
    }

    private static Optional<String> getPreAuthenticated(final RequestContext requestContext) {
        return getRequestHeader(requestContext, HttpHeader.X_DITTO_PRE_AUTH.getName())
                .or(() -> getRequestHeader(requestContext, HttpHeader.X_DITTO_DUMMY_AUTH.getName()))
                .or(() -> getRequestParam(requestContext, HttpHeader.X_DITTO_PRE_AUTH))
                .or(() -> getRequestParam(requestContext, HttpHeader.X_DITTO_DUMMY_AUTH));
    }

    private static Optional<String> getRequestParam(final RequestContext requestContext, final HttpHeader header) {
        return requestContext.getRequest().getUri().query().get(header.getName());
    }

    private static List<AuthorizationSubject> extractAuthorizationSubjects(final String subjectsCommaSeparated) {
        if (subjectsCommaSeparated != null && !subjectsCommaSeparated.isEmpty()) {
            return Arrays.stream(subjectsCommaSeparated.split(","))
                    .map(AuthorizationModelFactory::newAuthSubject)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static DittoRuntimeException buildFailedToExtractAuthorizationSubjectsException(
            final String preAuthenticatedSubject, final DittoHeaders dittoHeaders) {

        return GatewayAuthenticationFailedException.newBuilder(
                "Failed to extract AuthorizationSubjects from pre-authenticated header value " +
                        "'" + preAuthenticatedSubject + "'.")
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static DittoRuntimeException buildNotApplicableException(final DittoHeaders dittoHeaders) {
        return GatewayAuthenticationFailedException.newBuilder("No pre-authenticated subject was provided.")
                .dittoHeaders(dittoHeaders)
                .build();
    }

}
