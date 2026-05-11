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

import com.typesafe.config.Config;

/**
 * Default implementation of {@link MongoDbTimeseriesAdapterConfig}. Loads from a Typesafe
 * {@link Config} — typically the {@code ditto.timeseries.adapter.mongodb} sub-tree — falling back
 * to documented defaults when keys are absent.
 */
@Immutable
public final class DefaultMongoDbTimeseriesAdapterConfig implements MongoDbTimeseriesAdapterConfig {

    /**
     * Default database name when {@code database} is not set in HOCON.
     */
    public static final String DEFAULT_DATABASE = "ditto_ts";

    /**
     * Default collection-name prefix when {@code collection-prefix} is not set in HOCON.
     */
    public static final String DEFAULT_COLLECTION_PREFIX = "ts_";

    /**
     * Default granularity when {@code granularity} is not set in HOCON.
     */
    public static final Granularity DEFAULT_GRANULARITY = Granularity.SECONDS;

    private static final String KEY_URI = "uri";
    private static final String KEY_DATABASE = "database";
    private static final String KEY_COLLECTION_PREFIX = "collection-prefix";
    private static final String KEY_GRANULARITY = "granularity";
    private static final String KEY_RETENTION = "retention";

    private final String uri;
    private final String database;
    private final String collectionPrefix;
    private final Granularity granularity;
    @Nullable private final Duration retention;

    private DefaultMongoDbTimeseriesAdapterConfig(final String uri,
            final String database,
            final String collectionPrefix,
            final Granularity granularity,
            @Nullable final Duration retention) {

        this.uri = uri;
        this.database = database;
        this.collectionPrefix = collectionPrefix;
        this.granularity = granularity;
        this.retention = retention;
    }

    /**
     * Returns a config with the given values and no retention configured.
     *
     * @param uri the MongoDB connection URI.
     * @param database the database name.
     * @param collectionPrefix the collection-name prefix.
     * @param granularity the time-series granularity.
     * @return the config.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultMongoDbTimeseriesAdapterConfig of(final String uri,
            final String database,
            final String collectionPrefix,
            final Granularity granularity) {

        return new DefaultMongoDbTimeseriesAdapterConfig(
                checkNotNull(uri, "uri"),
                checkNotNull(database, "database"),
                checkNotNull(collectionPrefix, "collectionPrefix"),
                checkNotNull(granularity, "granularity"),
                null);
    }

    /**
     * Returns a config with the given values, including an explicit retention duration.
     *
     * @param uri the MongoDB connection URI.
     * @param database the database name.
     * @param collectionPrefix the collection-name prefix.
     * @param granularity the time-series granularity.
     * @param retention retention duration; {@code null} disables expiration.
     * @return the config.
     * @throws NullPointerException if {@code uri}, {@code database}, {@code collectionPrefix} or
     * {@code granularity} is {@code null}.
     */
    public static DefaultMongoDbTimeseriesAdapterConfig of(final String uri,
            final String database,
            final String collectionPrefix,
            final Granularity granularity,
            @Nullable final Duration retention) {

        return new DefaultMongoDbTimeseriesAdapterConfig(
                checkNotNull(uri, "uri"),
                checkNotNull(database, "database"),
                checkNotNull(collectionPrefix, "collectionPrefix"),
                checkNotNull(granularity, "granularity"),
                retention);
    }

    /**
     * Loads the config from the given Typesafe {@link Config} sub-tree, applying defaults for any
     * absent keys. The {@code uri} key is required.
     *
     * @param config the {@code mongodb} sub-tree (e.g. {@code ditto.timeseries.adapter.mongodb}).
     * @return the loaded config.
     * @throws NullPointerException if {@code config} is {@code null}.
     * @throws IllegalArgumentException if {@code uri} is missing or {@code granularity} is set to
     * an unknown value.
     */
    public static DefaultMongoDbTimeseriesAdapterConfig of(final Config config) {
        checkNotNull(config, "config");

        if (!config.hasPath(KEY_URI)) {
            throw new DittoConfigError(
                    "Required config key <timeseries.adapter.mongodb.uri> is missing.");
        }
        final String uri = config.getString(KEY_URI);
        final String database = stringOrDefault(config, KEY_DATABASE, DEFAULT_DATABASE);
        final String collectionPrefix =
                stringOrDefault(config, KEY_COLLECTION_PREFIX, DEFAULT_COLLECTION_PREFIX);
        final Granularity granularity = parseGranularity(config);
        final Duration retention = config.hasPath(KEY_RETENTION)
                ? config.getDuration(KEY_RETENTION)
                : null;

        return new DefaultMongoDbTimeseriesAdapterConfig(uri, database, collectionPrefix, granularity,
                retention);
    }

    @Override
    public String getUri() {
        return uri;
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
    public String getDatabase() {
        return database;
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
        return Objects.equals(uri, that.uri) &&
                Objects.equals(database, that.database) &&
                Objects.equals(collectionPrefix, that.collectionPrefix) &&
                granularity == that.granularity &&
                Objects.equals(retention, that.retention);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, database, collectionPrefix, granularity, retention);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "uri=" + uri +
                ", database=" + database +
                ", collectionPrefix=" + collectionPrefix +
                ", granularity=" + granularity +
                ", retention=" + retention +
                "]";
    }
}
