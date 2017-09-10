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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractActorSystem;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.redirect;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive ensuring that proxied requests only come via HTTPs.
 */
public final class HttpsEnsuringDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpsEnsuringDirective.class);

    private static final String X_FORWARDED_PROTO_STANDARD = "X-Forwarded-Proto";
    public static final String X_FORWARDED_PROTO_LBAAS = "x_forwarded_proto"; // LBaaS sets this value

    private static final String HTTPS_PROTO = "https";
    private static final String HTTPS_TEXT =
            "Connection via plain HTTP not supported, please connect via HTTPS instead";

    private static final AtomicBoolean FORCE_HTTPS_DISABLED_ALREADY_LOGGED = new AtomicBoolean(false);

    private HttpsEnsuringDirective() {
        // no op
    }

    /**
     * Ensures that proxied requests only come via HTTPs rejects all others - with one exception: Depending on the
     * configuration of {@link ConfigKeys#REDIRECT_TO_HTTPS} and
     * {@link ConfigKeys#REDIRECT_TO_HTTPS_BLACKLIST_PATTERN}, the requests are redirected to https instead of rejected.
     * <p>NOTE: The HTTPs check can completely disabled by configuration of {@link ConfigKeys#FORCE_HTTPS}.</p>
     *
     * @param correlationId the correlationId (used for logging)
     * @param inner the inner route to be wrapped with the HTTPs check
     * @return the new route wrapping {@code inner} with the HTTPs check
     */
    public static Route ensureHttps(final String correlationId, final Supplier<Route> inner) {
        return extractActorSystem(actorSystem -> extractRequestContext(
                requestContext -> enhanceLogWithCorrelationId(correlationId, () -> {
                    final Config config = actorSystem.settings().config();

                    final boolean forceHttps = config.getBoolean(ConfigKeys.FORCE_HTTPS);
                    if (!forceHttps) {
                        if (FORCE_HTTPS_DISABLED_ALREADY_LOGGED.compareAndSet(false, true)) {
                            LOGGER.warn("No HTTPS is enforced");
                        }
                        return inner.get();
                    }

                    final boolean redirectToHttps = config.getBoolean(ConfigKeys.REDIRECT_TO_HTTPS);
                    final String redirectToHttpsBlacklistPatternString =
                            config.getString(ConfigKeys.REDIRECT_TO_HTTPS_BLACKLIST_PATTERN);
                    final Pattern redirectToHttpsBlacklistPattern =
                            Pattern.compile(redirectToHttpsBlacklistPatternString);

                    // check whether the request came from HTTPS (before Proxy which terminated SSL and called us via HTTP)
                    final Uri requestUri = requestContext.getRequest().getUri();
                    final Optional<String> forwardedProtoHeader =
                            extractXForwardedProtoHeader(requestUri, requestContext);

                    if (!HTTPS_PROTO.equalsIgnoreCase(forwardedProtoHeader.orElse(null))) {
                        return handleNonHttpsRequest(requestUri, redirectToHttps, redirectToHttpsBlacklistPattern);
                    } else {
                        return inner.get();
                    }
                })));
    }

    private static Optional<String> extractXForwardedProtoHeader(final Uri requestUri,
            final RequestContext requestContext) {
        String forwardedProtoHeaderValue;

        final Optional<akka.http.javadsl.model.HttpHeader> standardForwardedProtoHeader = requestContext.getRequest()
                .getHeader(X_FORWARDED_PROTO_STANDARD) //
                .filter(header -> header.value().length() > 0);
        forwardedProtoHeaderValue = standardForwardedProtoHeader.map(akka.http.javadsl.model.HttpHeader::value)
                .orElse(null);
        if (forwardedProtoHeaderValue != null) {
            LOGGER.debug("Header {} was: '{}' for uri: {}", X_FORWARDED_PROTO_STANDARD, forwardedProtoHeaderValue,
                    requestUri);
        } else {
            final Optional<akka.http.javadsl.model.HttpHeader> forwardedProtoHeaderLbaas = requestContext.getRequest()
                    .getHeader(X_FORWARDED_PROTO_LBAAS) //
                    .filter(header -> header.value().length() > 0);
            forwardedProtoHeaderValue = forwardedProtoHeaderLbaas.map(akka.http.javadsl.model.HttpHeader::value)
                    .orElse(null);

            if (forwardedProtoHeaderValue != null) {
                LOGGER.debug("Header {} was: '{}' for uri: {}", X_FORWARDED_PROTO_LBAAS, forwardedProtoHeaderValue,
                        requestUri);
            } else {
                LOGGER.debug("Missing header {} for uri: {}", X_FORWARDED_PROTO_STANDARD + " or " +
                        X_FORWARDED_PROTO_LBAAS, requestUri);
            }
        }

        return Optional.ofNullable(forwardedProtoHeaderValue);
    }

    private static Route handleNonHttpsRequest(final Uri requestUri, final boolean redirectToHttps,
            final Pattern redirectToHttpsBlacklistPattern) {
        if (redirectToHttps && !redirectToHttpsBlacklistPattern.matcher(requestUri.getPathString()).matches()) {
            return redirectToHttps(requestUri);
        } else {
            return disallowRequest(requestUri);
        }
    }

    private static Route redirectToHttps(final Uri originalUri) {
        final Uri httpsUri = originalUri.scheme(HTTPS_PROTO);

        LOGGER.debug("Redirecting uri '{}' to '{}'.", originalUri, httpsUri);
        return redirect(httpsUri, StatusCodes.MOVED_PERMANENTLY);
    }

    private static Route disallowRequest(final Uri requestUri) {
        LOGGER.info("REST request on uri '{}' did not originate via HTTPS, sending back '{}'", requestUri,
                StatusCodes.NOT_FOUND);
        return complete(StatusCodes.NOT_FOUND, HTTPS_TEXT);
    }
}
