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

    private static final String KEY_COLLECTION_PREFIX = "collection-prefix";
    private static final String KEY_GRANULARITY = "granularity";
    private static final String KEY_RETENTION = "retention";

    private final MongoDbConfig mongoDbConfig;
    private final String collectionPrefix;
    private final Granularity granularity;
    @Nullable private final Duration retention;

    private DefaultMongoDbTimeseriesAdapterConfig(final MongoDbConfig mongoDbConfig,
            final String collectionPrefix,
            final Granularity granularity,
            @Nullable final Duration retention) {

        this.mongoDbConfig = mongoDbConfig;
        this.collectionPrefix = collectionPrefix;
        this.granularity = granularity;
        this.retention = retention;
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
                null);
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
                retention);
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

        return new DefaultMongoDbTimeseriesAdapterConfig(mongoDbConfig, collectionPrefix,
                granularity, retention);
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
                Objects.equals(retention, that.retention);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mongoDbConfig, collectionPrefix, granularity, retention);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mongoDbConfig=" + mongoDbConfig +
                ", collectionPrefix=" + collectionPrefix +
                ", granularity=" + granularity +
                ", retention=" + retention +
                "]";
    }
}
