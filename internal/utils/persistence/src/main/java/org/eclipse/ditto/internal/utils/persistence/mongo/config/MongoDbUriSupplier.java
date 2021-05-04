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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

/**
 * Supplies the MongoDB URI with additional options specified in the Config.
 * The path of the options supposed to be {@value DefaultOptionsConfig#CONFIG_PATH}.
 * If the originally configured MongoDB URI already contained options those will be overwritten.
 */
@Immutable
final class MongoDbUriSupplier implements Supplier<String> {

    /**
     * The supposed path of the MongoDB URI within the MongoDB Config object.
     */
    static final String URI_CONFIG_PATH = "uri";

    private final URI mongoDbSourceUri;
    private final Map<String, Object> extraUriOptions;

    private MongoDbUriSupplier(final URI mongoDbSourceUri, final Map<String, Object> extraUriOptions) {
        this.mongoDbSourceUri = mongoDbSourceUri;
        this.extraUriOptions = Collections.unmodifiableMap(new HashMap<>(extraUriOptions));
    }

    /**
     * Returns an instance of {@code MongoDbUriSupplier} based on the given MongoDB URI and extra-uri-options.
     *
     * @param configuredMongoUri the configured connection URI.
     * @param extraUriOptions additional options to add to the URI.
     * @return the instance.
     * @throws DittoConfigError if any argument is {@code null} or if {@code configuredMongoUri} was not a suitable
     * MongoDB URI.
     */
    static MongoDbUriSupplier of(final String configuredMongoUri, final Map<String, Object> extraUriOptions) {
        try {
            checkNotNull(configuredMongoUri, "configuredMongoUri");
            checkNotNull(extraUriOptions, "extraUriOptions");
        } catch (final NullPointerException e) {
            throw new DittoConfigError(e.getMessage(), e);
        }
        return new MongoDbUriSupplier(tryToGetMongoDbSourceUri(configuredMongoUri), extraUriOptions);
    }

    private static URI tryToGetMongoDbSourceUri(final String mongoDbUriString) {
        try {
            return new URI(mongoDbUriString);
        } catch (final URISyntaxException e) {
            // MongoDB URI is mis-configured. There is nothing we can do beside making the caller crash.
            final String msgTemplate = "The configured MongoDB URI <{0}> is invalid!";
            throw new DittoConfigError(MessageFormat.format(msgTemplate, mongoDbUriString), e);
        }
    }

    /**
     * @throws DittoConfigError if the URI cannot be created. See the error's cause and message for details.
     */
    @Override
    public String get() {
        final URI targetUri = tryToCreateMongoDbTargetUri();
        return targetUri.toASCIIString();
    }

    private URI tryToCreateMongoDbTargetUri() {
        try {
            return createMongoDbTargetUri();
        } catch (final URISyntaxException e) {
            final String msgTemplate = "Failed to create MongoDB URI based on <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgTemplate, mongoDbSourceUri), e);
        }
    }

    private URI createMongoDbTargetUri() throws URISyntaxException {
        return new URI(mongoDbSourceUri.getScheme(), mongoDbSourceUri.getAuthority(), mongoDbSourceUri.getPath(),
                getTargetQueryComponent(), mongoDbSourceUri.getFragment());
    }

    @Nullable
    private String getTargetQueryComponent() {
        return MongoDbUriEnhancer.of(extraUriOptions).apply(mongoDbSourceUri);
    }

    /**
     * Extends the query parameters of a provided MongoDB URI by the configured MongoDB connection options.
     * The order of the original parameters will be preserved.
     * The returned query component is encoded and is save to create a new URI.
     */
    @Immutable
    static final class MongoDbUriEnhancer implements Function<URI, String> {

        private final Map<String, Object> extraUriOptions;

        private MongoDbUriEnhancer(final Map<String, Object> extraUriOptions) {
            this.extraUriOptions = extraUriOptions;
        }

        /**
         * Returns an instance of {@code MongoDbUriEnhancer} for the given MongoDB connection options Config.
         *
         * @param extraUriOptions Map which provides the extra MongoDB connection options to add to the URI.
         * @return the instance.
         * @throws NullPointerException if {@code extraUriOptions} is {@code null}.
         */
        static MongoDbUriEnhancer of(final Map<String, Object> extraUriOptions) {
            checkNotNull(extraUriOptions, "The MongoDB extraUriOptions config must not be null!");
            return new MongoDbUriEnhancer(extraUriOptions);
        }

        @Nullable
        @Override
        public String apply(final URI mongoDBUri) {
            checkNotNull(mongoDBUri, "The MongoDB URI must not be null!");
            return getEncodedQueryString(putMongoDbConnectionOptions(parseQueryComponent(mongoDBUri.getQuery())));
        }

        private static Map<String, String> parseQueryComponent(@Nullable final String originalUriQueryComponent) {
            final QueryComponentParser queryComponentParser = QueryComponentParser.getInstance();
            return queryComponentParser.apply(originalUriQueryComponent);
        }

        private Map<String, String> putMongoDbConnectionOptions(final Map<String, String> queryParameters) {
            // null values are not present in the entry set, the values of those parameters are unchanged.
            for (final Map.Entry<String, Object> connectionOptionEntry : extraUriOptions.entrySet()) {
                queryParameters.put(connectionOptionEntry.getKey(), String.valueOf(connectionOptionEntry.getValue()));
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
            final String encodedParameterName = encodeWithUtf8(entry.getKey());
            final String encodedParameterValue = encodeWithUtf8(entry.getValue());

            return String.format("%s=%s", encodedParameterName, encodedParameterValue);
        }

        private static String encodeWithUtf8(final String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }

    }

    /**
     * Parses a given query component of a URI and returns the result as a Map of string keys and string values.
     */
    @Immutable
    static final class QueryComponentParser implements Function<String, Map<String, String>> {

        private static final QueryComponentParser INSTANCE = new QueryComponentParser();

        private QueryComponentParser() {
            super();
        }

        /**
         * Returns an instance of {@code QueryComponentParser}.
         *
         * @return the instance.
         */
        static QueryComponentParser getInstance() {
            return INSTANCE;
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

}
