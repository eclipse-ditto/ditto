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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractActorSystem;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.redirect;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.slf4j.Logger;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive ensuring that proxied requests only come via HTTPs.
 */
public final class HttpsEnsuringDirective {

    private static final String HTTPS_PROTO = "https";
    private static final String HTTPS_TEXT =
            "Connection via plain HTTP not supported, please connect via HTTPS instead";

    private static final AtomicBoolean FORCE_HTTPS_DISABLED_ALREADY_LOGGED = new AtomicBoolean(false);

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(HttpsEnsuringDirective.class);

    private final HttpConfig httpConfig;

    private HttpsEnsuringDirective(final HttpConfig httpConfig) {
        this.httpConfig = checkNotNull(httpConfig, "HTTP config");
    }

    /**
     * Returns an instance of {@code HttpsEnsuringDirective}.
     *
     * @param httpConfig the configuration settings of the Gateway service's HTTP behaviour.
     * @return the instance.
     * @throws NullPointerException if {@code httpConfig} is {@code null}.
     */
    public static HttpsEnsuringDirective getInstance(final HttpConfig httpConfig) {
        return new HttpsEnsuringDirective(httpConfig);
    }

    /**
     * Ensures that proxied requests only come via HTTPs rejects all others - with one exception: Depending on the
     * configuration regarding HTTP enforcement, the requests are redirected to HTTPS instead of being rejected.
     * <p>
     * NOTE: The HTTPS check can be completely disabled by configuration.
     * </p>
     *
     * @param correlationId the correlationId (used for logging).
     * @param inner the inner route to be wrapped with the HTTPs check.
     * @return the new route wrapping {@code inner} with the HTTPs check.
     */
    public Route ensureHttps(@Nullable final CharSequence correlationId, final Supplier<Route> inner) {
        final ThreadSafeDittoLogger logger;
        if (null != correlationId) {
            logger = LOGGER.withCorrelationId(correlationId);
        } else {
            logger = LOGGER;
        }
        return extractActorSystem(actorSystem -> extractRequestContext(
                requestContext -> {
                    if (!httpConfig.isForceHttps()) {
                        if (FORCE_HTTPS_DISABLED_ALREADY_LOGGED.compareAndSet(false, true)) {
                            logger.warn("No HTTPS is enforced!");
                        }
                        return inner.get();
                    }

                    // check whether the request came from HTTPS (before Proxy which terminated SSL and called us via
                    // HTTP)
                    final Uri requestUri = requestContext.getRequest().getUri();
                    final String forwardedProtoHeaderOrNull =
                            getForwardedProtoHeaderOrNull(requestUri, requestContext, logger);
                    if (!HTTPS_PROTO.equalsIgnoreCase(forwardedProtoHeaderOrNull)) {
                        return handleNonHttpsRequest(requestUri, logger);
                    }
                    return inner.get();
                }));
    }

    private String getForwardedProtoHeaderOrNull(final Uri requestUri, final RequestContext requestContext,
            final Logger logger) {

        final String result = getHeader(requestContext.getRequest(), httpConfig.getProtocolHeaders())
                .orElseGet(requestUri::getScheme);

        logger.debug("Read protocol <{}> from headers <{}> or from URI <{}>.", result, requestUri,
                httpConfig.getProtocolHeaders());

        return result;
    }

    private Route handleNonHttpsRequest(final Uri requestUri, final Logger logger) {
        if (httpConfig.isRedirectToHttps() && !isBlocked(requestUri.getPathString())) {
            return redirectToHttps(requestUri, logger);
        }
        return disallowRequest(requestUri, logger);
    }

    private boolean isBlocked(final CharSequence requestUriPath) {
        final Pattern redirectToHttpsBlocklistPattern = httpConfig.getRedirectToHttpsBlocklistPattern();
        final Matcher matcher = redirectToHttpsBlocklistPattern.matcher(requestUriPath);
        return matcher.matches();
    }

    private static Route redirectToHttps(final Uri originalUri, final Logger logger) {
        final Uri httpsUri = originalUri.scheme(HTTPS_PROTO);

        logger.debug("Redirecting URI <{}> to <{}>.", originalUri, httpsUri);
        return redirect(httpsUri, StatusCodes.MOVED_PERMANENTLY);
    }

    private static Route disallowRequest(final Uri requestUri, final Logger logger) {
        logger.info("REST request on URI <{}> did not originate via HTTPS, sending back <{}>.", requestUri,
                StatusCodes.NOT_FOUND);
        return complete(StatusCodes.NOT_FOUND, HTTPS_TEXT);
    }

    private static Optional<String> getHeader(final HttpRequest request, final List<String> protocolHeaderNames) {
        for (final var headerName : protocolHeaderNames) {
            final var header = request.getHeader(headerName);
            if (header.isPresent()) {
                return header.map(HttpHeader::value);
            }
        }
        return Optional.empty();
    }

}
