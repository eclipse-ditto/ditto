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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractActorSystem;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.redirect;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive ensuring that proxied requests only come via HTTPs.
 */
public final class HttpsEnsuringDirective {

    public static final String X_FORWARDED_PROTO_LBAAS = "x_forwarded_proto"; // LBaaS sets this value

    private static final String X_FORWARDED_PROTO_STANDARD = "X-Forwarded-Proto";

    private static final String HTTPS_PROTO = "https";
    private static final String HTTPS_TEXT =
            "Connection via plain HTTP not supported, please connect via HTTPS instead";

    private static final AtomicBoolean FORCE_HTTPS_DISABLED_ALREADY_LOGGED = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpsEnsuringDirective.class);

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
     * @param correlationId the correlationId (used for logging)
     * @param inner the inner route to be wrapped with the HTTPs check
     * @return the new route wrapping {@code inner} with the HTTPs check
     */
    public Route ensureHttps(final String correlationId, final Supplier<Route> inner) {
        return extractActorSystem(actorSystem -> extractRequestContext(
                requestContext -> enhanceLogWithCorrelationId(correlationId, () -> {
                    if (!httpConfig.isForceHttps()) {
                        if (FORCE_HTTPS_DISABLED_ALREADY_LOGGED.compareAndSet(false, true)) {
                            LOGGER.warn("No HTTPS is enforced!");
                        }
                        return inner.get();
                    }

                    // check whether the request came from HTTPS (before Proxy which terminated SSL and called us via
                    // HTTP)
                    final Uri requestUri = requestContext.getRequest().getUri();
                    if (!HTTPS_PROTO.equalsIgnoreCase(getForwardedProtoHeaderOrNull(requestUri, requestContext))) {
                        return handleNonHttpsRequest(requestUri);
                    }
                    return inner.get();
                })));
    }

    @Nullable
    private static String getForwardedProtoHeaderOrNull(final Uri requestUri, final RequestContext requestContext) {
        @Nullable final String result = requestContext.getRequest()
                .getHeader(X_FORWARDED_PROTO_STANDARD)
                .map(HttpHeader::value)
                .filter(value -> !value.isEmpty())
                .orElseGet(() -> requestContext.getRequest()
                        .getHeader(X_FORWARDED_PROTO_LBAAS)
                        .map(HttpHeader::value)
                        .filter(value -> !value.isEmpty())
                        .orElse(null));

        if (null != result) {
            LOGGER.debug("Header <{}> was <{}> for URI <{}>.", X_FORWARDED_PROTO_STANDARD, result, requestUri);
        } else {
            LOGGER.debug("Neither header <{}> nor <{}> set for URI <{}>.", X_FORWARDED_PROTO_STANDARD,
                    X_FORWARDED_PROTO_LBAAS, requestUri);
        }

        return result;
    }

    private Route handleNonHttpsRequest(final Uri requestUri) {
        if (httpConfig.isRedirectToHttps() && !isBlacklisted(requestUri.getPathString())) {
            return redirectToHttps(requestUri);
        }
        return disallowRequest(requestUri);
    }

    private boolean isBlacklisted(final CharSequence requestUriPath) {
        final Pattern redirectToHttpsBlacklistPattern = httpConfig.getRedirectToHttpsBlacklistPattern();
        final Matcher matcher = redirectToHttpsBlacklistPattern.matcher(requestUriPath);
        return matcher.matches();
    }

    private static Route redirectToHttps(final Uri originalUri) {
        final Uri httpsUri = originalUri.scheme(HTTPS_PROTO);

        LOGGER.debug("Redirecting URI <{}> to <{}>.", originalUri, httpsUri);
        return redirect(httpsUri, StatusCodes.MOVED_PERMANENTLY);
    }

    private static Route disallowRequest(final Uri requestUri) {
        LOGGER.info("REST request on URI <{}> did not originate via HTTPS, sending back <{}>.", requestUri,
                StatusCodes.NOT_FOUND);
        return complete(StatusCodes.NOT_FOUND, HTTPS_TEXT);
    }

}
