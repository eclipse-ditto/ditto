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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
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
import org.eclipse.ditto.services.gateway.security.utils.HttpUtils;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by using a defined header field {@link HttpHeader#X_DITTO_PRE_AUTH} which proxies in front
 * of Ditto may set to inject authenticated subjects into a HTTP request.
 * <p>
 * Enabled/disabled by
 * {@link org.eclipse.ditto.services.gateway.util.config.security.AuthenticationConfig#isPreAuthenticationEnabled()}.
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
        super(LOGGER);
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

    private static boolean containsHeader(final RequestContext requestContext, final HttpHeader header) {
        final Optional<String> requestHeader = HttpUtils.getRequestHeader(requestContext, header.getName());
        return requestHeader.isPresent() || getRequestParam(requestContext, header).isPresent();
    }

    private static Optional<String> getRequestParam(final RequestContext requestContext, final HttpHeader header) {
        final HttpRequest httpRequest = requestContext.getRequest();
        final Uri requestUri = httpRequest.getUri();
        final Query query = requestUri.query();
        return query.get(header.getName());
    }

    @Override
    protected AuthenticationResult tryToAuthenticate(final RequestContext requestContext,
            final DittoHeaders dittoHeaders) {

        final Optional<String> preAuthOpt = getPreAuthenticated(requestContext);

        if (preAuthOpt.isEmpty()) {
            return DefaultAuthenticationResult.failed(dittoHeaders, getAuthenticationFailedException(dittoHeaders));
        }

        final String preAuthenticatedSubject = preAuthOpt.get();

        final List<AuthorizationSubject> authorizationSubjects = getAuthorizationSubjects(preAuthenticatedSubject);
        if (authorizationSubjects.isEmpty()) {
            return toFailedAuthenticationResult(
                    buildFailedToExtractAuthorizationSubjectsException(preAuthenticatedSubject, dittoHeaders),
                    dittoHeaders);
        }

        final AuthorizationContext authContext =
                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                        authorizationSubjects);

        LOGGER.withCorrelationId(dittoHeaders)
                .info("Pre-authentication has been applied resulting in AuthorizationContext <{}>.", authContext);

        return DefaultAuthenticationResult.successful(dittoHeaders, authContext);
    }

    private static Optional<String> getPreAuthenticated(final RequestContext requestContext) {
        return HttpUtils.getRequestHeader(requestContext, HttpHeader.X_DITTO_PRE_AUTH.getName())
                .or(() -> HttpUtils.getRequestHeader(requestContext, HttpHeader.X_DITTO_DUMMY_AUTH.getName()))
                .or(() -> getRequestParam(requestContext, HttpHeader.X_DITTO_PRE_AUTH))
                .or(() -> getRequestParam(requestContext, HttpHeader.X_DITTO_DUMMY_AUTH));
    }

    private static DittoRuntimeException getAuthenticationFailedException(final DittoHeaders dittoHeaders) {
        return GatewayAuthenticationFailedException.newBuilder("No pre-authenticated subject was provided!")
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static List<AuthorizationSubject> getAuthorizationSubjects(final String subjectsCommaSeparated) {
        return Arrays.stream(subjectsCommaSeparated.split(","))
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
    }

    private static DittoRuntimeException buildFailedToExtractAuthorizationSubjectsException(
            final String preAuthenticatedSubject, final DittoHeaders dittoHeaders) {

        final String mPtrn = "Failed to extract AuthorizationSubjects from pre-authenticated header value <{0}>!";
        return GatewayAuthenticationFailedException.newBuilder(MessageFormat.format(mPtrn, preAuthenticatedSubject))
                .dittoHeaders(dittoHeaders)
                .build();
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

}
