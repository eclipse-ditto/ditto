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
package org.eclipse.ditto.timeseries.mongodb;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link MongoDbTimeseriesAdapterConfig}.
 * <p>
 * Connection settings (URI / IAM / pool / SSL) come from the shared {@link MongoDbConfig} that
 * the sibling Ditto services also use. Only the time-series-specific tuning
 * ({@code collection-prefix}, {@code granularity}, {@code retention}) is loaded from the
 * {@code ditto.timeseries.adapter.mongodb} sub-tree of HOCON.
 */
@Immutable
public final class DefaultMongoDbTimeseriesAdapterConfig implements MongoDbTimeseriesAdapterConfig {

    /**
     * Default collection-name prefix when {@code collection-prefix} is not set in HOCON.
     */
    public static final String DEFAULT_COLLECTION_PREFIX = "ts_";

    /**
     * Default granularity when {@code granularity} is not set in HOCON.
     */
    public static final Granularity DEFAULT_GRANULARITY = Granularity.SECONDS;

    /**
     * Default scan ceiling (per path) when {@code max-query-result-size} is not set in HOCON.
     */
    public static final int DEFAULT_MAX_QUERY_RESULT_SIZE = 1_000_000;

    /**
     * Default per-read server-side time budget when {@code query-timeout} is not set in HOCON.
     */
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(60);

    private static final String KEY_COLLECTION_PREFIX = "collection-prefix";
    private static final String KEY_GRANULARITY = "granularity";
    private static final String KEY_RETENTION = "retention";
    private static final String KEY_MAX_QUERY_RESULT_SIZE = "max-query-result-size";
    private static final String KEY_QUERY_TIMEOUT = "query-timeout";

    private final MongoDbConfig mongoDbConfig;
    private final String collectionPrefix;
    private final Granularity granularity;
    @Nullable private final Duration retention;
    private final int maxQueryResultSize;
    private final Duration queryTimeout;

    private DefaultMongoDbTimeseriesAdapterConfig(final MongoDbConfig mongoDbConfig,
            final String collectionPrefix,
            final Granularity granularity,
            @Nullable final Duration retention,
            final int maxQueryResultSize,
            final Duration queryTimeout) {

        this.mongoDbConfig = mongoDbConfig;
        this.collectionPrefix = collectionPrefix;
        this.granularity = granularity;
        this.retention = retention;
        this.maxQueryResultSize = maxQueryResultSize;
        this.queryTimeout = queryTimeout;
    }

    /**
     * Returns a config with the given values and no retention configured.
     *
     * @param mongoDbConfig the shared Ditto MongoDB connection config (URI, IAM, pool, SSL).
     * @param collectionPrefix the collection-name prefix.
     * @param granularity the time-series granularity.
     * @return the config.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultMongoDbTimeseriesAdapterConfig of(final MongoDbConfig mongoDbConfig,
            final String collectionPrefix,
            final Granularity granularity) {

        return new DefaultMongoDbTimeseriesAdapterConfig(
                checkNotNull(mongoDbConfig, "mongoDbConfig"),
                checkNotNull(collectionPrefix, "collectionPrefix"),
                checkNotNull(granularity, "granularity"),
                null,
                DEFAULT_MAX_QUERY_RESULT_SIZE,
                DEFAULT_QUERY_TIMEOUT);
    }

    /**
     * Returns a config with the given values, including an explicit retention duration.
     *
     * @param mongoDbConfig the shared Ditto MongoDB connection config.
     * @param collectionPrefix the collection-name prefix.
     * @param granularity the time-series granularity.
     * @param retention retention duration; {@code null} disables expiration.
     * @return the config.
     * @throws NullPointerException if {@code mongoDbConfig}, {@code collectionPrefix} or
     * {@code granularity} is {@code null}.
     */
    public static DefaultMongoDbTimeseriesAdapterConfig of(final MongoDbConfig mongoDbConfig,
            final String collectionPrefix,
            final Granularity granularity,
            @Nullable final Duration retention) {

        return new DefaultMongoDbTimeseriesAdapterConfig(
                checkNotNull(mongoDbConfig, "mongoDbConfig"),
                checkNotNull(collectionPrefix, "collectionPrefix"),
                checkNotNull(granularity, "granularity"),
                retention,
                DEFAULT_MAX_QUERY_RESULT_SIZE,
                DEFAULT_QUERY_TIMEOUT);
    }

    /**
     * Loads the config from the {@code ditto.timeseries.adapter.mongodb} sub-tree, applying
     * defaults for any absent keys.
     *
     * @param mongoDbConfig the shared Ditto MongoDB connection config (built from
     * {@code ditto.mongodb} by {@code DefaultMongoDbConfig.of(…)}).
     * @param adapterConfig the {@code mongodb} sub-tree of {@code ditto.timeseries.adapter}.
     * @return the loaded config.
     * @throws NullPointerException if either argument is {@code null}.
     * @throws DittoConfigError if {@code granularity} is set to an unknown value.
     */
    public static DefaultMongoDbTimeseriesAdapterConfig of(final MongoDbConfig mongoDbConfig,
            final Config adapterConfig) {

        checkNotNull(mongoDbConfig, "mongoDbConfig");
        checkNotNull(adapterConfig, "adapterConfig");

        final String collectionPrefix =
                stringOrDefault(adapterConfig, KEY_COLLECTION_PREFIX, DEFAULT_COLLECTION_PREFIX);
        final Granularity granularity = parseGranularity(adapterConfig);
        final Duration retention = adapterConfig.hasPath(KEY_RETENTION)
                ? adapterConfig.getDuration(KEY_RETENTION)
                : null;
        final int maxQueryResultSize = adapterConfig.hasPath(KEY_MAX_QUERY_RESULT_SIZE)
                ? adapterConfig.getInt(KEY_MAX_QUERY_RESULT_SIZE)
                : DEFAULT_MAX_QUERY_RESULT_SIZE;
        final Duration queryTimeout = adapterConfig.hasPath(KEY_QUERY_TIMEOUT)
                ? adapterConfig.getDuration(KEY_QUERY_TIMEOUT)
                : DEFAULT_QUERY_TIMEOUT;

        // Both are safety ceilings, and both have a value that MongoDB reinterprets as "no limit"
        // (limit(0) returns all documents; maxTime(0) disables the time budget). Reject those — a
        // mis-set guard that silently turns itself off is worse than a clear startup failure.
        if (maxQueryResultSize <= 0) {
            throw new DittoConfigError("Configuration <" + KEY_MAX_QUERY_RESULT_SIZE +
                    "> must be a positive number of data points but was <" + maxQueryResultSize + ">.");
        }
        if (queryTimeout.isZero() || queryTimeout.isNegative()) {
            throw new DittoConfigError("Configuration <" + KEY_QUERY_TIMEOUT +
                    "> must be a positive duration but was <" + queryTimeout + ">.");
        }

        return new DefaultMongoDbTimeseriesAdapterConfig(mongoDbConfig, collectionPrefix,
                granularity, retention, maxQueryResultSize, queryTimeout);
    }

    private static String stringOrDefault(final Config config, final String key,
            final String fallback) {

        return config.hasPath(key) ? config.getString(key) : fallback;
    }

    private static Granularity parseGranularity(final Config config) {
        if (!config.hasPath(KEY_GRANULARITY)) {
            return DEFAULT_GRANULARITY;
        }
        final String raw = config.getString(KEY_GRANULARITY);
        return Granularity.forName(raw).orElseThrow(() -> new DittoConfigError(
                "Unknown timeseries.adapter.mongodb.granularity <" + raw + ">. " +
                        "Expected one of: seconds, minutes, hours."));
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return mongoDbConfig;
    }

    @Override
    public String getCollectionPrefix() {
        return collectionPrefix;
    }

    @Override
    public Granularity getGranularity() {
        return granularity;
    }

    @Override
    public Optional<Duration> getRetention() {
        return Optional.ofNullable(retention);
    }

    @Override
    public int getMaxQueryResultSize() {
        return maxQueryResultSize;
    }

    @Override
    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultMongoDbTimeseriesAdapterConfig)) {
            return false;
        }
        final DefaultMongoDbTimeseriesAdapterConfig that = (DefaultMongoDbTimeseriesAdapterConfig) o;
        return Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(collectionPrefix, that.collectionPrefix) &&
                granularity == that.granularity &&
                Objects.equals(retention, that.retention) &&
                maxQueryResultSize == that.maxQueryResultSize &&
                Objects.equals(queryTimeout, that.queryTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mongoDbConfig, collectionPrefix, granularity, retention,
                maxQueryResultSize, queryTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mongoDbConfig=" + mongoDbConfig +
                ", collectionPrefix=" + collectionPrefix +
                ", granularity=" + granularity +
                ", retention=" + retention +
                ", maxQueryResultSize=" + maxQueryResultSize +
                ", queryTimeout=" + queryTimeout +
                "]";
    }
}
