/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;

/**
 * Provides methods to redact sensitive parameter/header values from (raw) URIs and headers e.g. for logging purposes.
 */
final class RequestLoggingFilter {

    private static final Set<String> FILTERED_PARAMETERS = Set.of("access_token");
    private static final Set<String> FILTERED_HEADERS = Set.of("authorization");
    private static final String REDACTED_VALUE = "***";

    private RequestLoggingFilter() {}

    /**
     * Determines whether a {@link akka.http.javadsl.model.Query} contains parameters that need filtering.
     *
     * @param query the query to check
     * @return {@code true} if the given Query requires filtering
     */
    static boolean requiresFiltering(final Query query) {
        return FILTERED_PARAMETERS.stream().anyMatch(param -> query.get(param).isPresent());
    }

    /**
     * Redacts unwanted parameter values with {@code ***} in a raw string uri.
     *
     * @param rawUri the raw uri to redact.
     * @return the redacted raw uri
     */
    static String filterRawUri(final String rawUri) {
        final int startFrom = rawUri.indexOf("?");
        if (startFrom >= 0) { // contains parameters
            String filteredRawUri = rawUri;
            for (final String parameter : FILTERED_PARAMETERS) {
                final int indexOfParameter = filteredRawUri.indexOf(parameter + "=");
                if (indexOfParameter >= 0) {
                    filteredRawUri = filteredRawUri.replaceAll(parameter + "=[^&]*", parameter + "=***");
                }
            }
            return filteredRawUri;
        } else {
            return rawUri;
        }
    }

    /**
     * Redacts unwanted parameter values with {@code ***} in a Uri.
     *
     * @param uri the uri to redact.
     * @return the redacted uri
     */
    static Uri filterUri(final Uri uri) {
        return requiresFiltering(uri.query()) ? uri.query(filterQuery(uri.query())) : uri;
    }

    /**
     * Redacts unwanted parameter values with {@code ***} in a Query object.
     *
     * @param query the query to redact.
     * @return the redacted query
     */
    static Query filterQuery(final Query query) {
        if (requiresFiltering(query)) {
            final Map<String, String> queryMap = new HashMap<>(query.toMap());
            FILTERED_PARAMETERS.forEach(param -> queryMap.put(param, REDACTED_VALUE));
            return Query.create(queryMap);
        } else {
            return query;
        }
    }

    /**
     * Redacts unwanted header values with {@code ***} in an iterable of {@link akka.http.javadsl.model.HttpHeader}s.
     *
     * @param headers the headers to redact
     * @return the redacted headers
     */
    static Iterable<HttpHeader> filterHeaders(final Iterable<HttpHeader> headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .map(header -> {
                    if (FILTERED_HEADERS.contains(header.lowercaseName())) {
                        return HttpHeader.parse(header.name(), REDACTED_VALUE);
                    } else {
                        return header;
                    }
                })
                .toList();
    }

}
