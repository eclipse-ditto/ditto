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
package org.eclipse.ditto.services.utils.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Constructs MongoDB URL from a source URL and configuration settings.
 */
public final class MongoConfig {

    private static final String PREFIX = "ditto.services-utils-config.mongodb";

    private static final String POOL_PREFIX = PREFIX + ".pool";

    /**
     * Config key of source MongoDB URI.
     */
    public static final String URI = PREFIX + ".uri";

    /**
     * Config key of connection string options.
     */
    public static final String OPTIONS = PREFIX + ".options";

    /**
     * Config key of maximum query duration. It is up to the Mongo client to respect it.
     */
    public static final String MAX_QUERY_TIME = PREFIX + ".maxQueryTime";

    /**
     * Config key of maximum number of connections allowed.
     */
    public static final String POOL_MAX_SIZE = POOL_PREFIX + ".maxSize";

    /**
     * Maximum number of waiters for a connection to become available from the pool.
     */
    public static final String POOL_MAX_WAIT_QUEUE_SIZE = POOL_PREFIX + ".maxWaitQueueSize";

    /**
     * Maximum number of seconds a thread waits for a connection to become available.
     */
    public static final String POOL_MAX_WAIT_TIME = POOL_PREFIX + ".maxWaitTimeSecs";

    /**
     * Fallback client configuration.
     */
    private static final Config fallbackMongoConfig;

    /* Fallback configuration values. */
    static {
        final Map<String, Object> fallbackMap = new HashMap<>();
        fallbackMap.put(POOL_MAX_SIZE, 100);
        fallbackMap.put(POOL_MAX_WAIT_QUEUE_SIZE, 100);
        fallbackMap.put(POOL_MAX_WAIT_TIME, Duration.ofSeconds(30));
        fallbackMap.put(MAX_QUERY_TIME, Duration.ofSeconds(60));
        fallbackMongoConfig = ConfigFactory.parseMap(fallbackMap);
    }

    private MongoConfig() {}

    /**
     * Retrieve maximum query duration.
     *
     * @param config The configuration.
     * @return The maximum query duration.
     */
    public static Duration getMaxQueryTime(final Config config) {
        return config.withFallback(fallbackMongoConfig).getDuration(MAX_QUERY_TIME);
    }

    /**
     * Retrieve the maximum number of connections in the connection pool.
     *
     * @param config The configuration.
     * @return The maximum number of connections.
     */
    public static int getPoolMaxSize(final Config config) {
        return config.withFallback(fallbackMongoConfig).getInt(POOL_MAX_SIZE);
    }

    /**
     * Retrieve the maximum number of threads waiting for a connection to become available.
     *
     * @param config The configuration.
     * @return The maximum number of waiting threads.
     */
    public static int getPoolMaxWaitQueueSize(final Config config) {
        return config.withFallback(fallbackMongoConfig).getInt(POOL_MAX_WAIT_QUEUE_SIZE);
    }

    /**
     * Retrieve the maximum time to wait for a connection to become available.
     *
     * @param config The configuration.
     * @return The maximum wait time.
     */
    public static Duration getPoolMaxWaitTime(final Config config) {
        return config.withFallback(fallbackMongoConfig).getDuration(POOL_MAX_WAIT_TIME);
    }

    /**
     * Computes MongoDB URI from configured source URI and MongoDB settings.
     *
     * @param config Config object containing source URI and MongoDB settings.
     * @return URI adapted from source URI with parameters set according to MongoDB settings.
     */
    public static String getMongoUri(final Config config) {
        try {
            final URI sourceUri = new URI(config.getString(URI));
            final LinkedHashMap<String, String> query = parseQuery(sourceUri.getQuery());

            // set connection string options
            final Config options = config.hasPath(OPTIONS) ? config.getConfig(OPTIONS) : ConfigFactory.empty();
            options.entrySet().forEach(
                    // null values are not present in the entry set, the values of those parameters are unchanged.
                    entry -> query.put(entry.getKey(), entry.getValue().unwrapped().toString()));

            final String targetQueryString = unparseQuery(query);
            final URI targetUri =
                    new URI(sourceUri.getScheme(), sourceUri.getAuthority(), sourceUri.getPath(),
                            targetQueryString.isEmpty() ? null : targetQueryString, sourceUri.getFragment());

            return targetUri.toASCIIString();
        } catch (final URISyntaxException e) {
            // Mongo URI is misconfigured. There is nothing we can do beside making the caller crash.
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Tests whether Mongo URI is defined in configuration.
     *
     * @param config The configuration object.
     * @return Whether Mongo URI is defined.
     */
    public static boolean isUriDefined(final Config config) {
        return config.hasPath(URI);
    }

    /**
     * Parses URI query string into a map between strings such that the ordering of the query parameters is preserved.
     *
     * @param queryString the query string.
     * @return A map object mapping parameter names to parameter values, the iteration order of whose map entries is the
     * order the parameters are listed in the query string. The return type is {@code LinkedHashMap} to signify the
     * order guarantee.
     */
    static LinkedHashMap<String, String> parseQuery(final String queryString) {
        final LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (queryString != null) {
            Arrays.stream(queryString.split("&")).forEach(paramDefinition -> {
                final int i = paramDefinition.indexOf("=");
                if (i >= 0) {
                    final String paramName = paramDefinition.substring(0, i);
                    final String paramValue = paramDefinition.substring(i + 1);
                    params.put(paramName, paramValue);
                }
            });
        }
        return params;
    }

    private static String unparseQuery(final LinkedHashMap<String, String> query) {
        return query.entrySet().stream().map(entry -> {
            // using deprecated URL encode method because the recommended method throws UnsupportedEncodingException
            final String parameterName = URLEncoder.encode(entry.getKey());
            final String parameterValue = URLEncoder.encode(entry.getValue());
            return String.format("%s=%s", parameterName, parameterValue);
        }).collect(Collectors.joining("&"));
    }
}
