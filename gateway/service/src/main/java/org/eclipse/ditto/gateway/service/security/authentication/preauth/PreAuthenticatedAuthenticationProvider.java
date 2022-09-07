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
package org.eclipse.ditto.gateway.service.security.authentication.preauth;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationContextType;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.service.security.HttpHeader;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.TimeMeasuringAuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.utils.HttpUtils;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by using a defined header field {@link org.eclipse.ditto.gateway.service.security.HttpHeader#X_DITTO_PRE_AUTH} which proxies in front
 * of Ditto may set to inject authenticated subjects into a HTTP request.
 * <p>
 * Enabled/disabled by
 * {@link org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig#isPreAuthenticationEnabled()}.
 * </p>
 * If this is enabled it is of upmost importance that only the proxy in front of Ditto sets the defined header field
 * {@link org.eclipse.ditto.gateway.service.security.HttpHeader#X_DITTO_PRE_AUTH}, otherwise anyone using the HTTP API may impersonate any other authorization
 * subject.
 *
 * @since 1.1.0
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class PreAuthenticatedAuthenticationProvider
        extends TimeMeasuringAuthenticationProvider<AuthenticationResult> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(PreAuthenticatedAuthenticationProvider.class);

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
        return containsHeader(requestContext, HttpHeader.X_DITTO_PRE_AUTH);
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
    protected CompletableFuture<AuthenticationResult> tryToAuthenticate(final RequestContext requestContext,
            final DittoHeaders dittoHeaders) {

        final Optional<String> preAuthOpt = getPreAuthenticated(requestContext);

        if (preAuthOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                    DefaultAuthenticationResult.failed(dittoHeaders, getAuthenticationFailedException(dittoHeaders)));
        }

        final String preAuthenticatedSubject = preAuthOpt.get();

        final List<AuthorizationSubject> authorizationSubjects = getAuthorizationSubjects(preAuthenticatedSubject);
        if (authorizationSubjects.isEmpty()) {
            return CompletableFuture.completedFuture(toFailedAuthenticationResult(
                    buildFailedToExtractAuthorizationSubjectsException(preAuthenticatedSubject, dittoHeaders),
                    dittoHeaders));
        }

        final AuthorizationContext authContext =
                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                        authorizationSubjects);

        LOGGER.withCorrelationId(dittoHeaders)
                .info("Pre-authentication has been applied resulting in AuthorizationContext <{}>.", authContext);

        return CompletableFuture.completedFuture(DefaultAuthenticationResult.successful(dittoHeaders, authContext));
    }

    private static Optional<String> getPreAuthenticated(final RequestContext requestContext) {
        return HttpUtils.getRequestHeader(requestContext, HttpHeader.X_DITTO_PRE_AUTH.getName())
                .or(() -> getRequestParam(requestContext, HttpHeader.X_DITTO_PRE_AUTH));
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
                .toList();
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
