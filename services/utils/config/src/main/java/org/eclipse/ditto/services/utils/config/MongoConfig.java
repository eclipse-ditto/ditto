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
package org.eclipse.ditto.services.utils.config;

import static java.util.Objects.requireNonNull;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

/**
 * Configuration settings for MongoDB.
 */
@Immutable
public final class MongoConfig {

    /**
     * The default maximum amount of seconds a MongoDB query may last.
     */
    public static final int MAX_QUERY_TIME_DEFAULT_SECS = 60;

    /**
     * Path of the MongoDB config within a global Config.
     */
    static final String CONFIG_PATH = "ditto.services-utils-config.mongodb";

    private static final String POOL_PREFIX = "pool";

    /**
     * Config key of source MongoDB URI.
     */
    private static final String URI = "uri";

    /**
     * Config key of connection string options.
     */
    private static final String OPTIONS = "options";

    /**
     * Config key of maximum query duration. It is up to the Mongo client to respect it.
     */
    private static final String MAX_QUERY_TIME = "maxQueryTime";

    /**
     * Config key of maximum number of connections allowed.
     */
    private static final String POOL_MAX_SIZE = POOL_PREFIX + ".maxSize";

    /**
     * Maximum number of waiters for a connection to become available from the pool.
     */
    private static final String POOL_MAX_WAIT_QUEUE_SIZE = POOL_PREFIX + ".maxWaitQueueSize";

    /**
     * Maximum number of seconds a thread waits for a connection to become available.
     */
    private static final String POOL_MAX_WAIT_TIME = POOL_PREFIX + ".maxWaitTimeSecs";

    /**
     * Whether a JMX connection pool listener should be added.
     */
    private static final String POOL_JMX_LISTENER_ENABLED = POOL_PREFIX + ".jmxListenerEnabled";

    /**
     * config key for mongodb ssl options.
     */
    private static final String SSL_ENABLED = OPTIONS + ".ssl";

    private static final int POOL_MAX_SIZE_DEFAULT = 100;
    private static final int POOL_MAX_WAIT_QUEUE_SIZE_DEFAULT = 100;
    private static final int POOL_MAX_WAIT_TIME_DEFAULT_SECS = 30;
    private static final boolean POOL_JMX_LISTENER_ENABLED_DEFAULT = false;

    private final Config mongoDbConfig;

    private MongoConfig(final Config theMongoDbConfig) {
        mongoDbConfig = theMongoDbConfig;
    }

    /**
     * Returns an instance of {@code MongoConfig} which tries to obtain its properties from the given Config.
     *
     * @param config the Config which contains nested MongoDB settings at path {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public static MongoConfig of(final Config config) {
        requireNonNull(config, "The Config must not be null!");
        final Config mongoDbSpecificConfig;
        if (config.hasPath(CONFIG_PATH)) {
            mongoDbSpecificConfig = config.getConfig(CONFIG_PATH).withFallback(getFallbackValues());
        } else {
            mongoDbSpecificConfig = getFallbackValues();
        }

        return new MongoConfig(mongoDbSpecificConfig);
    }

    private static Config getFallbackValues() {
        final Map<String, Object> fallbackValues = new HashMap<>(5);
        fallbackValues.put(POOL_MAX_SIZE, POOL_MAX_SIZE_DEFAULT);
        fallbackValues.put(POOL_MAX_WAIT_QUEUE_SIZE, POOL_MAX_WAIT_QUEUE_SIZE_DEFAULT);
        fallbackValues.put(POOL_MAX_WAIT_TIME, Duration.ofSeconds(POOL_MAX_WAIT_TIME_DEFAULT_SECS));
        fallbackValues.put(POOL_JMX_LISTENER_ENABLED, POOL_JMX_LISTENER_ENABLED_DEFAULT);
        fallbackValues.put(MAX_QUERY_TIME, Duration.ofSeconds(MAX_QUERY_TIME_DEFAULT_SECS));

        return ConfigFactory.parseMap(fallbackValues);
    }

    /**
     * Retrieve the maximum query duration.
     *
     * @return the duration.
     */
    public Duration getMaxQueryTime() {
        return mongoDbConfig.getDuration(MAX_QUERY_TIME);
    }

    /**
     * Retrieve the maximum number of connections in the connection pool.
     *
     * @return the maximum number of connections.
     */
    public int getConnectionPoolMaxSize() {
        return mongoDbConfig.getInt(POOL_MAX_SIZE);
    }

    /**
     * Retrieve the maximum number of threads waiting for a connection to become available.
     *
     * @return the maximum number of waiting threads.
     */
    public int getConnectionPoolMaxWaitQueueSize() {
        return mongoDbConfig.getInt(POOL_MAX_WAIT_QUEUE_SIZE);
    }

    /**
     * Retrieve the maximum time to wait for a connection to become available.
     *
     * @return the maximum wait time.
     */
    public Duration getConnectionPoolMaxWaitTime() {
        return mongoDbConfig.getDuration(POOL_MAX_WAIT_TIME);
    }

    /**
     * Indicates whether a JMX {@code ConnectionPoolListener} should be added.
     *
     * @return {@code true} if a JMX ConnectionPoolListener should be added, {@code false} else.
     */
    public boolean isJmxListenerEnabled() {
        if (mongoDbConfig.hasPath(POOL_JMX_LISTENER_ENABLED)) {
            return mongoDbConfig.getBoolean(POOL_JMX_LISTENER_ENABLED);
        }
        return false;
    }

    /**
     * Indicates whether SSL should be enabled for the configured MongoDB source.
     *
     * @return {@code true} if SSL should be enabled, {@code false} else.
     */
    public boolean isSslEnabled() {
        if (mongoDbConfig.hasPath(SSL_ENABLED)) {
            return mongoDbConfig.getBoolean(SSL_ENABLED);
        }
        return false;
    }

    /**
     * Indicates whether the MongoDB URI is defined in this configuration.
     *
     * @return {@code true} if the MongoDB URI is defined, {@code false} else.
     */
    public boolean isUriDefined() {
        return mongoDbConfig.hasPath(URI);
    }


    /**
     * Retrieves the MongoDB URI from configured source URI and MongoDB settings.
     *
     * @return the URI adapted from source URI with parameters set according to MongoDB settings.
     * @throws com.typesafe.config.ConfigException.Missing if the Config does not have a value for {@value #URI} key.
     * @throws com.typesafe.config.ConfigException.WrongType if the Config does not have a string value for
     * {@value #URI} key.
     * @throws java.lang.IllegalStateException if the configured MongoDB URI is invalid or if extending the configured
     * MongoDB URI by the configured connection options failed.
     * @see #isUriDefined()
     */
    public String getMongoUri() {
        final URI targetUri = tryToCreateMongoDbTargetUri(tryToGetMongoDbSourceUri(mongoDbConfig.getString(URI)));
        return targetUri.toASCIIString();
    }

    private static URI tryToGetMongoDbSourceUri(final String mongoDbUriString) {
        try {
            return new URI(mongoDbUriString);
        } catch (final URISyntaxException e) {
            // MongoDB URI is mis-configured. There is nothing we can do beside making the caller crash.
            final String msgTemplate = "The configured MongoDB URI <{0}> is invalid!";
            throw new IllegalStateException(MessageFormat.format(msgTemplate, mongoDbUriString), e);
        }
    }

    private URI tryToCreateMongoDbTargetUri(final URI mongoDbSourceUri) {
        try {
            return createMongoDbTargetUri(mongoDbSourceUri);
        } catch (final URISyntaxException e) {
            final String msgTemplate = "Failed to create MongoDB URI based on <{0}>!";
            throw new IllegalStateException(MessageFormat.format(msgTemplate, mongoDbSourceUri), e);
        }
    }

    private URI createMongoDbTargetUri(final URI mongoDbSourceUri) throws URISyntaxException {
        return new URI(mongoDbSourceUri.getScheme(), mongoDbSourceUri.getAuthority(), mongoDbSourceUri.getPath(),
                getTargetQueryComponent(mongoDbSourceUri), mongoDbSourceUri.getFragment());
    }

    @Nullable
    private String getTargetQueryComponent(final URI sourceUri) {
        final Config mongoDbConnectionOptions = mongoDbConfig.hasPath(OPTIONS)
                ? mongoDbConfig.getConfig(OPTIONS)
                : ConfigFactory.empty();

        return MongoDbUriEnhancer.of(mongoDbConnectionOptions).apply(sourceUri);
    }

    /**
     * Parses a given query component of a URI and returns the result as a Map of string keys and string values.
     */
    @Immutable
    static final class QueryComponentParser implements Function<String, Map<String, String>> {

        private QueryComponentParser() {
            super();
        }

        /**
         * Returns an instance of {@code QueryComponentParser}.
         *
         * @return the instance.
         */
        static QueryComponentParser getInstance() {
            return new QueryComponentParser();
        }

        @Override
        public Map<String, String> apply(@Nullable final String queryComponent) {
            final Map<String, String> result = new LinkedHashMap<>();
            if (null == queryComponent) {
                return result;
            }
            for (final String queryParameter : queryComponent.split("&")) {
                final int assignmentDelimiterIndex = queryParameter.indexOf('=');
                if (-1 != assignmentDelimiterIndex) {
                    final String parameterName = queryParameter.substring(0, assignmentDelimiterIndex);
                    final String parameterValue = queryParameter.substring(assignmentDelimiterIndex + 1);
                    result.put(parameterName, parameterValue);
                }
            }

            return result;
        }

    }

    /**
     * Extends the query parameters of a provided MongoDB URI by the configured MongoDB connection options.
     * The order of the original parameters will be preserved.
     * The returned query component is encoded and is save to create a new URI.
     */
    @Immutable
    static final class MongoDbUriEnhancer implements Function<URI, String> {

        private final Config connectionOptions;

        private MongoDbUriEnhancer(final Config theConnectionOptions) {
            connectionOptions = theConnectionOptions;
        }

        /**
         * Returns an instance of {@code MongoDbUriEnhancer} for the given MongoDB connection options Config.
         *
         * @param connectionOptions Config which provides the MongoDB connection options.
         * @return the instance.
         * @throws NullPointerException if {@code connectionOptions} is {@code null}.
         */
        static MongoDbUriEnhancer of(final Config connectionOptions) {
            requireNonNull(connectionOptions, "The MongoDB connection options config must not be null!");
            return new MongoDbUriEnhancer(connectionOptions);
        }

        @Nullable
        @Override
        public String apply(final URI mongoDBUri) {
            requireNonNull(mongoDBUri, "The MongoDB URI must not be null!");
            return getEncodedQueryString(putMongoDbConnectionOptions(parseQueryComponent(mongoDBUri.getQuery())));
        }

        private static Map<String, String> parseQueryComponent(@Nullable final String originalUriQueryComponent) {
            final QueryComponentParser queryComponentParser = QueryComponentParser.getInstance();
            return queryComponentParser.apply(originalUriQueryComponent);
        }

        private Map<String, String> putMongoDbConnectionOptions(final Map<String, String> queryParameters) {
            // null values are not present in the entry set, the values of those parameters are unchanged.
            for (final Map.Entry<String, ConfigValue> connectionOptionEntry : connectionOptions.entrySet()) {
                final ConfigValue connectionOptionValue = connectionOptionEntry.getValue();
                final Object rawConnectionOptionValue = connectionOptionValue.unwrapped();

                queryParameters.put(connectionOptionEntry.getKey(), rawConnectionOptionValue.toString());
            }

            return queryParameters;
        }

        @Nullable
        private static String getEncodedQueryString(final Map<String, String> query) {
            if (query.isEmpty()) {
                return null;
            }

            return query.entrySet()
                    .stream()
                    .map(MongoDbUriEnhancer::getEncodeQueryParameterString)
                    .collect(Collectors.joining("&"));
        }

        private static String getEncodeQueryParameterString(final Map.Entry<String, String> entry) {
            // using deprecated URL encode method because the recommended method throws UnsupportedEncodingException
            final String encodedParameterName = tryToEncodeWithUtf8(entry.getKey());
            final String encodedParameterValue = tryToEncodeWithUtf8(entry.getValue());

            return String.format("%s=%s", encodedParameterName, encodedParameterValue);
        }

        private static String tryToEncodeWithUtf8(final String s) {
            try {
                return encodeWithUtf8(s);
            } catch (final UnsupportedEncodingException e) {
                final String msgTemplate = "Failed to URL-encode <{0}> with UTF-8!";
                throw new IllegalStateException(MessageFormat.format(msgTemplate, s), e);
            }
        }

        private static String encodeWithUtf8(final String s) throws UnsupportedEncodingException {
            return URLEncoder.encode(s, "UTF-8");
        }

    }

}
