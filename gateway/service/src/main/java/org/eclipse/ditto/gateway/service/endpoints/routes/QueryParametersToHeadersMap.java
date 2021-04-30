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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;

/**
 * This class converts a given Map of query parameters into a Map of header key-value pairs.
 * The query parameters which are converted are provided by {@link org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig#getQueryParametersAsHeaders()}.
 *
 * @since 1.1.0
 */
@Immutable
public final class QueryParametersToHeadersMap implements UnaryOperator<Map<String, String>> {

    private final Set<HeaderDefinition> queryParametersAsHeaders;

    private QueryParametersToHeadersMap(final Set<HeaderDefinition> queryParametersAsHeaders) {
        this.queryParametersAsHeaders = queryParametersAsHeaders;
    }

    /**
     * Returns an instance of {@code QueryParametersToHeadersMap}.
     *
     * @param httpConfig the configuration setting which provide the header keys which should be derived from query
     * parameter names.
     * @return the instance.
     * @throws NullPointerException if {@code httpConfig} is {@code null}.
     */
    public static QueryParametersToHeadersMap getInstance(final HttpConfig httpConfig) {
        checkNotNull(httpConfig, "httpConfig");
        return new QueryParametersToHeadersMap(httpConfig.getQueryParametersAsHeaders());
    }

    @Override
    public Map<String, String> apply(final Map<String, String> queryParameters) {
        checkNotNull(queryParameters, "queryParameters");

        final Map<String, String> result = new HashMap<>(queryParameters.size());
        queryParametersAsHeaders.forEach(headerDefinition -> {
            final String headerKey = headerDefinition.getKey();
            @Nullable final String queryParamValue = queryParameters.get(headerKey);
            if (null != queryParamValue) {
                result.put(headerKey, queryParamValue);
            }
        });
        return result;
    }

}
