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
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.timeseries.api.Capabilities;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;

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

    /**
     * Aggregations MongoDB pushes down to the DB engine by default: the single-accumulator group
     * aggregations (version-independent) plus {@code derivative} and {@code integral} (native window
     * operators on 5.0+, exact matches for the kernel and bounded server-side). {@code percentile}
     * is intentionally absent — native {@code $percentile} is approximate, so it is opt-in — and
     * {@code rate} has no native operator.
     */
    public static final Set<Aggregation> DEFAULT_PUSHABLE_AGGREGATIONS = Collections.unmodifiableSet(
            EnumSet.of(Aggregation.AVG, Aggregation.MIN, Aggregation.MAX, Aggregation.SUM,
                    Aggregation.COUNT, Aggregation.FIRST, Aggregation.LAST, Aggregation.STDDEV,
                    Aggregation.DERIVATIVE, Aggregation.INTEGRAL));

    /**
     * Fill strategies MongoDB applies natively by default via {@code $densify}/{@code $fill} (5.3+):
     * {@code linear} and {@code previous}, both exact matches for the kernel.
     */
    public static final Set<FillStrategy> DEFAULT_NATIVE_FILL_STRATEGIES = Collections.unmodifiableSet(
            EnumSet.of(FillStrategy.LINEAR, FillStrategy.PREVIOUS));

    /**
     * Capabilities for a modern MongoDB (5.0+) when the {@code capabilities} block is absent: a
     * complete native query, the {@link #DEFAULT_PUSHABLE_AGGREGATIONS} and the
     * {@link #DEFAULT_NATIVE_FILL_STRATEGIES}. Kept in sync with the shipped {@code timeseries.conf}
     * defaults so tests and production agree.
     */
    public static final Capabilities DEFAULT_CAPABILITIES = Capabilities.builder()
            .supportsNativeQuery(true)
            .pushableAggregations(DEFAULT_PUSHABLE_AGGREGATIONS)
            .nativeFillStrategies(DEFAULT_NATIVE_FILL_STRATEGIES)
            .build();

    private static final String KEY_COLLECTION_PREFIX = "collection-prefix";
    private static final String KEY_GRANULARITY = "granularity";
    private static final String KEY_RETENTION = "retention";
    private static final String KEY_RETENTION_OVERRIDES = "retention-overrides";
    private static final String KEY_MAX_QUERY_RESULT_SIZE = "max-query-result-size";
    private static final String KEY_QUERY_TIMEOUT = "query-timeout";
    private static final String KEY_CAPABILITIES = "capabilities";
    private static final String KEY_CAP_NATIVE_QUERY = "native-query";
    private static final String KEY_CAP_PUSHABLE_AGGREGATIONS = "pushable-aggregations";
    private static final String KEY_CAP_NATIVE_FILL = "native-fill-strategies";

    /** Sentinel values that disable expiration (mapped to no {@code expireAfter} / {@code collMod off}). */
    private static final java.util.Set<String> UNLIMITED_TOKENS =
            java.util.Set.of("", "unlimited", "off", "none");

    private final MongoDbConfig mongoDbConfig;
    private final String collectionPrefix;
    private final Granularity granularity;
    @Nullable private final Duration retention;
    private final Map<String, Duration> retentionOverrides;
    private final int maxQueryResultSize;
    private final Duration queryTimeout;
    private final Capabilities capabilities;

    private DefaultMongoDbTimeseriesAdapterConfig(final MongoDbConfig mongoDbConfig,
            final String collectionPrefix,
            final Granularity granularity,
            @Nullable final Duration retention,
            final Map<String, Duration> retentionOverrides,
            final int maxQueryResultSize,
            final Duration queryTimeout,
            final Capabilities capabilities) {

        this.mongoDbConfig = mongoDbConfig;
        this.collectionPrefix = collectionPrefix;
        this.granularity = granularity;
        this.retention = retention;
        this.retentionOverrides = Map.copyOf(retentionOverrides);
        this.maxQueryResultSize = maxQueryResultSize;
        this.queryTimeout = queryTimeout;
        this.capabilities = capabilities;
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
                Map.of(),
                DEFAULT_MAX_QUERY_RESULT_SIZE,
                DEFAULT_QUERY_TIMEOUT,
                DEFAULT_CAPABILITIES);
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
                Map.of(),
                DEFAULT_MAX_QUERY_RESULT_SIZE,
                DEFAULT_QUERY_TIMEOUT,
                DEFAULT_CAPABILITIES);
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
        final Duration retention = parseRetention(adapterConfig, KEY_RETENTION);
        final Map<String, Duration> retentionOverrides = parseRetentionOverrides(adapterConfig);
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
                granularity, retention, retentionOverrides, maxQueryResultSize, queryTimeout,
                parseCapabilities(adapterConfig));
    }

    /**
     * Parses the {@code capabilities} block. Absent block &rarr; {@link #DEFAULT_CAPABILITIES}; an
     * absent key within the block falls back to that key's default.
     */
    private static Capabilities parseCapabilities(final Config adapterConfig) {
        if (!adapterConfig.hasPath(KEY_CAPABILITIES)) {
            return DEFAULT_CAPABILITIES;
        }
        final Config config = adapterConfig.getConfig(KEY_CAPABILITIES);
        return Capabilities.builder()
                .supportsNativeQuery(booleanOrDefault(config, KEY_CAP_NATIVE_QUERY, true))
                .pushableAggregations(parsePushableAggregations(config))
                .nativeFillStrategies(parseNativeFillStrategies(config))
                .build();
    }

    private static boolean booleanOrDefault(final Config config, final String key,
            final boolean fallback) {
        return config.hasPath(key) ? config.getBoolean(key) : fallback;
    }

    private static Set<Aggregation> parsePushableAggregations(final Config capabilitiesConfig) {
        if (!capabilitiesConfig.hasPath(KEY_CAP_PUSHABLE_AGGREGATIONS)) {
            return DEFAULT_PUSHABLE_AGGREGATIONS;
        }
        final EnumSet<Aggregation> set = EnumSet.noneOf(Aggregation.class);
        for (final String raw : capabilitiesConfig.getStringList(KEY_CAP_PUSHABLE_AGGREGATIONS)) {
            set.add(Aggregation.forName(raw).orElseThrow(() -> new DittoConfigError(
                    "Unknown timeseries.adapter.mongodb." + KEY_CAPABILITIES + "." +
                            KEY_CAP_PUSHABLE_AGGREGATIONS + " entry <" + raw + ">.")));
        }
        return set;
    }

    private static Set<FillStrategy> parseNativeFillStrategies(final Config capabilitiesConfig) {
        if (!capabilitiesConfig.hasPath(KEY_CAP_NATIVE_FILL)) {
            return DEFAULT_NATIVE_FILL_STRATEGIES;
        }
        final EnumSet<FillStrategy> set = EnumSet.noneOf(FillStrategy.class);
        for (final String raw : capabilitiesConfig.getStringList(KEY_CAP_NATIVE_FILL)) {
            set.add(FillStrategy.forName(raw).orElseThrow(() -> new DittoConfigError(
                    "Unknown timeseries.adapter.mongodb." + KEY_CAPABILITIES + "." +
                            KEY_CAP_NATIVE_FILL + " entry <" + raw + ">.")));
        }
        return set;
    }

    private static String stringOrDefault(final Config config, final String key,
            final String fallback) {

        return config.hasPath(key) ? config.getString(key) : fallback;
    }

    /**
     * Parses the default retention. Absent or a sentinel token ({@code unlimited}/{@code off}/
     * {@code none}/empty) means no expiration ({@code null}); otherwise a HOCON duration that must
     * be strictly positive (MongoDB treats {@code expireAfterSeconds: 0} as "expire immediately").
     */
    @Nullable
    private static Duration parseRetention(final Config config, final String key) {
        if (!config.hasPath(key)) {
            return null;
        }
        if (isUnlimitedToken(config, key)) {
            return null;
        }
        final Duration duration = config.getDuration(key);
        requirePositiveRetention(key, duration);
        return duration;
    }

    private static Map<String, Duration> parseRetentionOverrides(final Config adapterConfig) {
        if (!adapterConfig.hasPath(KEY_RETENTION_OVERRIDES)) {
            return Map.of();
        }
        final Config overridesConfig = adapterConfig.getConfig(KEY_RETENTION_OVERRIDES);
        final Map<String, Duration> overrides = new LinkedHashMap<>();
        for (final Map.Entry<String, ConfigValue> entry : overridesConfig.root().entrySet()) {
            // Namespace keys contain dots, which HOCON treats as path separators — quote them so the
            // whole namespace is read as a single key rather than a nested path.
            final String namespace = entry.getKey();
            final String path = ConfigUtil.quoteString(namespace);
            final String overrideKey = KEY_RETENTION_OVERRIDES + "." + namespace;
            if (isUnlimitedToken(overridesConfig, path)) {
                throw new DittoConfigError("Configuration <" + overrideKey + "> must be a positive " +
                        "duration; per-namespace \"unlimited\" is not supported — set the default " +
                        "<" + KEY_RETENTION + "> to unlimited instead.");
            }
            final Duration duration = overridesConfig.getDuration(path);
            requirePositiveRetention(overrideKey, duration);
            overrides.put(namespace, duration);
        }
        return Map.copyOf(overrides);
    }

    private static boolean isUnlimitedToken(final Config config, final String path) {
        try {
            return UNLIMITED_TOKENS.contains(config.getString(path).trim().toLowerCase(java.util.Locale.ROOT));
        } catch (final com.typesafe.config.ConfigException.WrongType e) {
            // Not a string (e.g. a number-of-seconds) — let the duration parser handle it.
            return false;
        }
    }

    private static void requirePositiveRetention(final String key, final Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            throw new DittoConfigError("Configuration <" + key + "> must be a positive duration " +
                    "(or \"unlimited\" to disable expiration) but was <" + duration + ">.");
        }
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
    public Map<String, Duration> getRetentionOverrides() {
        return retentionOverrides;
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
    public Capabilities getCapabilities() {
        return capabilities;
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
                Objects.equals(retentionOverrides, that.retentionOverrides) &&
                maxQueryResultSize == that.maxQueryResultSize &&
                Objects.equals(queryTimeout, that.queryTimeout) &&
                Objects.equals(capabilities, that.capabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mongoDbConfig, collectionPrefix, granularity, retention,
                retentionOverrides, maxQueryResultSize, queryTimeout, capabilities);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mongoDbConfig=" + mongoDbConfig +
                ", collectionPrefix=" + collectionPrefix +
                ", granularity=" + granularity +
                ", retention=" + retention +
                ", retentionOverrides=" + retentionOverrides +
                ", maxQueryResultSize=" + maxQueryResultSize +
                ", queryTimeout=" + queryTimeout +
                ", capabilities=" + capabilities +
                "]";
    }
}
