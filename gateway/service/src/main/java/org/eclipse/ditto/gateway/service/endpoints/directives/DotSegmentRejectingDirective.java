/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractRequest;

import java.text.MessageFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.RawRequestURI;
import org.apache.pekko.http.javadsl.server.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Pekko Http directive rejecting requests whose raw request path contains dot segments ({@code .} or
 * {@code ..}), also when the dots are percent-encoded (e.g. {@code %2e%2e}) or when the segment is only delimited by a
 * percent-encoded slash ({@code %2f}, e.g. {@code a%2f..%2fb}).
 * <p>
 * Pekko HTTP silently removes (unencoded) dot segments when it builds the effective request URI, so such a request
 * would be routed to a different resource than the one named in the raw path. Reverse proxies in front of the gateway
 * may interpret the same raw path differently: e.g. nginx decodes {@code %2f} before resolving dot segments, while the
 * gateway treats {@code %2f} as part of a path segment. Both allow a request to be routed through a proxy location
 * which was not intended for it. Dot segments are not needed for any Ditto API, so they are rejected with
 * {@code 400 Bad Request}.
 * </p>
 * <p>
 * The check requires the {@code Raw-Request-URI} header (Pekko HTTP setting
 * {@code pekko.http.server.raw-request-uri-header = on}, which is the gateway's default). Without it, the request URI
 * already has the dot segments removed and the check has no effect.
 * </p>
 */
public final class DotSegmentRejectingDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(DotSegmentRejectingDirective.class);

    private static final String URI_DOT_SEGMENTS_TEMPLATE =
            "URI ''{0}'' contains dot segments (''.'' or ''..'') in its path, which are not supported.";

    private static final Pattern SCHEME_AND_AUTHORITY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*://[^/?#]*");
    private static final Pattern ENCODED_SLASH = Pattern.compile("%2f", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCODED_DOT = Pattern.compile("%2e", Pattern.CASE_INSENSITIVE);

    private DotSegmentRejectingDirective() {
        throw new AssertionError();
    }

    /**
     * Rejects requests with dot segments in their raw request path with {@code 400 Bad Request}, passes all other
     * requests to the {@code inner} route.
     *
     * @param inner the inner route.
     * @return the route.
     */
    public static Route rejectDotSegments(final Supplier<Route> inner) {
        return extractRequest(request -> {
            final String rawRequestUri = getRawRequestUri(request);
            if (containsDotSegment(rawRequestUri)) {
                LOGGER.info("Rejecting request with dot segments in its path: <{}>", rawRequestUri);
                return complete(StatusCodes.BAD_REQUEST,
                        MessageFormat.format(URI_DOT_SEGMENTS_TEMPLATE, rawRequestUri));
            }
            return inner.get();
        });
    }

    private static String getRawRequestUri(final HttpRequest request) {
        return request.getHeader(RawRequestURI.class)
                .map(RawRequestURI::uri)
                .orElseGet(() -> {
                    LOGGER.debug("raw-request-uri-header is not enabled, checking the already normalized URI");
                    return request.getUri().toString();
                });
    }

    /**
     * Determines whether the path of the passed raw request URI (origin-form or absolute-form) contains a dot segment
     * ({@code .} or {@code ..}), also with percent-encoded dots or percent-encoded slashes as delimiters.
     *
     * @param rawRequestUri the raw request URI as sent by the client.
     * @return {@code true} if the path contains a dot segment.
     */
    static boolean containsDotSegment(final String rawRequestUri) {
        // decode "%2f" first, as proxies like nginx treat it as a path separator when resolving dot segments
        final String path = ENCODED_SLASH.matcher(extractRawPath(rawRequestUri)).replaceAll("/");
        for (final String segment : path.split("/", -1)) {
            final String decodedDots = ENCODED_DOT.matcher(segment).replaceAll(".");
            if (".".equals(decodedDots) || "..".equals(decodedDots)) {
                return true;
            }
        }
        return false;
    }

    private static String extractRawPath(final String rawRequestUri) {
        final String withoutSchemeAndAuthority = SCHEME_AND_AUTHORITY.matcher(rawRequestUri).replaceFirst("");
        final int queryOrFragmentStart = indexOfQueryOrFragment(withoutSchemeAndAuthority);
        if (queryOrFragmentStart >= 0) {
            return withoutSchemeAndAuthority.substring(0, queryOrFragmentStart);
        }
        return withoutSchemeAndAuthority;
    }

    private static int indexOfQueryOrFragment(final String path) {
        final int queryStart = path.indexOf('?');
        final int fragmentStart = path.indexOf('#');
        if (queryStart < 0) {
            return fragmentStart;
        } else if (fragmentStart < 0) {
            return queryStart;
        } else {
            return Math.min(queryStart, fragmentStart);
        }
    }

}
