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
package org.eclipse.ditto.timeseries.iotdb;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;

/**
 * Default implementation of {@link IoTDbTimeseriesAdapterConfig}, loaded from the
 * {@code ditto.timeseries.adapter.iotdb} sub-tree of HOCON.
 */
@Immutable
public final class DefaultIoTDbTimeseriesAdapterConfig implements IoTDbTimeseriesAdapterConfig {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 6667;
    public static final String DEFAULT_USERNAME = "root";
    public static final String DEFAULT_PASSWORD = "root";
    public static final String DEFAULT_DATABASE = "ditto_ts";
    public static final String DEFAULT_TABLE_PREFIX = "ts_";
    public static final int DEFAULT_MAX_SESSIONS = 16;
    public static final int DEFAULT_MAX_QUERY_RESULT_SIZE = 1_000_000;
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(60);

    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_DATABASE = "database";
    private static final String KEY_TABLE_PREFIX = "table-prefix";
    private static final String KEY_MAX_SESSIONS = "max-sessions";
    private static final String KEY_RETENTION = "retention";
    private static final String KEY_RETENTION_OVERRIDES = "retention-overrides";
    private static final String KEY_MAX_QUERY_RESULT_SIZE = "max-query-result-size";
    private static final String KEY_QUERY_TIMEOUT = "query-timeout";

    private static final Set<String> UNLIMITED_TOKENS = Set.of("", "unlimited", "off", "none");

    // Database and table names end up as SQL identifiers.
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern TABLE_PREFIX = Pattern.compile("[a-z][a-z0-9_]*|");

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String database;
    private final String tablePrefix;
    private final int maxSessions;
    @Nullable private final Duration retention;
    private final Map<String, Duration> retentionOverrides;
    private final int maxQueryResultSize;
    private final Duration queryTimeout;

    private DefaultIoTDbTimeseriesAdapterConfig(final String host,
            final int port,
            final String username,
            final String password,
            final String database,
            final String tablePrefix,
            final int maxSessions,
            @Nullable final Duration retention,
            final Map<String, Duration> retentionOverrides,
            final int maxQueryResultSize,
            final Duration queryTimeout) {

        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
        this.tablePrefix = tablePrefix;
        this.maxSessions = maxSessions;
        this.retention = retention;
        this.retentionOverrides = Map.copyOf(retentionOverrides);
        this.maxQueryResultSize = maxQueryResultSize;
        this.queryTimeout = queryTimeout;
    }

    /**
     * Loads the config from the {@code ditto.timeseries.adapter.iotdb} sub-tree, applying defaults for absent keys.
     *
     * @param adapterConfig the {@code iotdb} sub-tree of {@code ditto.timeseries.adapter}.
     * @return the loaded config.
     * @throws NullPointerException if {@code adapterConfig} is {@code null}.
     * @throws DittoConfigError if a value is invalid.
     */
    public static DefaultIoTDbTimeseriesAdapterConfig of(final Config adapterConfig) {
        checkNotNull(adapterConfig, "adapterConfig");

        final String database = stringOrDefault(adapterConfig, KEY_DATABASE, DEFAULT_DATABASE);
        if (!IDENTIFIER.matcher(database).matches()) {
            throw new DittoConfigError("Configuration <" + KEY_DATABASE + "> must match " + IDENTIFIER +
                    " but was <" + database + ">.");
        }
        final String tablePrefix = stringOrDefault(adapterConfig, KEY_TABLE_PREFIX, DEFAULT_TABLE_PREFIX);
        if (!TABLE_PREFIX.matcher(tablePrefix).matches()) {
            throw new DittoConfigError("Configuration <" + KEY_TABLE_PREFIX + "> must match " + TABLE_PREFIX +
                    " but was <" + tablePrefix + ">.");
        }
        final int port = intOrDefault(adapterConfig, KEY_PORT, DEFAULT_PORT);
        final int maxSessions = intOrDefault(adapterConfig, KEY_MAX_SESSIONS, DEFAULT_MAX_SESSIONS);
        final int maxQueryResultSize =
                intOrDefault(adapterConfig, KEY_MAX_QUERY_RESULT_SIZE, DEFAULT_MAX_QUERY_RESULT_SIZE);
        final Duration queryTimeout = adapterConfig.hasPath(KEY_QUERY_TIMEOUT)
                ? adapterConfig.getDuration(KEY_QUERY_TIMEOUT)
                : DEFAULT_QUERY_TIMEOUT;
        requirePositive(KEY_PORT, port);
        requirePositive(KEY_MAX_SESSIONS, maxSessions);
        requirePositive(KEY_MAX_QUERY_RESULT_SIZE, maxQueryResultSize);
        if (queryTimeout.isZero() || queryTimeout.isNegative()) {
            throw new DittoConfigError("Configuration <" + KEY_QUERY_TIMEOUT +
                    "> must be a positive duration but was <" + queryTimeout + ">.");
        }

        return new DefaultIoTDbTimeseriesAdapterConfig(
                stringOrDefault(adapterConfig, KEY_HOST, DEFAULT_HOST),
                port,
                stringOrDefault(adapterConfig, KEY_USERNAME, DEFAULT_USERNAME),
                stringOrDefault(adapterConfig, KEY_PASSWORD, DEFAULT_PASSWORD),
                database,
                tablePrefix,
                maxSessions,
                parseRetention(adapterConfig),
                parseRetentionOverrides(adapterConfig),
                maxQueryResultSize,
                queryTimeout);
    }

    private static String stringOrDefault(final Config config, final String key, final String fallback) {
        return config.hasPath(key) ? config.getString(key) : fallback;
    }

    private static int intOrDefault(final Config config, final String key, final int fallback) {
        return config.hasPath(key) ? config.getInt(key) : fallback;
    }

    private static void requirePositive(final String key, final int value) {
        if (value <= 0) {
            throw new DittoConfigError("Configuration <" + key + "> must be positive but was <" + value + ">.");
        }
    }

    @Nullable
    private static Duration parseRetention(final Config config) {
        if (!config.hasPath(KEY_RETENTION) || isUnlimitedToken(config, KEY_RETENTION)) {
            return null;
        }
        final Duration duration = config.getDuration(KEY_RETENTION);
        requirePositiveRetention(KEY_RETENTION, duration);
        return duration;
    }

    private static Map<String, Duration> parseRetentionOverrides(final Config adapterConfig) {
        if (!adapterConfig.hasPath(KEY_RETENTION_OVERRIDES)) {
            return Map.of();
        }
        final Config overridesConfig = adapterConfig.getConfig(KEY_RETENTION_OVERRIDES);
        final Map<String, Duration> overrides = new LinkedHashMap<>();
        for (final Map.Entry<String, ConfigValue> entry : overridesConfig.root().entrySet()) {
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
        return overrides;
    }

    private static boolean isUnlimitedToken(final Config config, final String path) {
        try {
            return UNLIMITED_TOKENS.contains(config.getString(path).trim().toLowerCase(Locale.ROOT));
        } catch (final ConfigException.WrongType e) {
            return false;
        }
    }

    private static void requirePositiveRetention(final String key, final Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            throw new DittoConfigError("Configuration <" + key + "> must be a positive duration " +
                    "(or \"unlimited\" to disable expiration) but was <" + duration + ">.");
        }
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getDatabase() {
        return database;
    }

    @Override
    public String getTablePrefix() {
        return tablePrefix;
    }

    @Override
    public int getMaxSessions() {
        return maxSessions;
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultIoTDbTimeseriesAdapterConfig that = (DefaultIoTDbTimeseriesAdapterConfig) o;
        return port == that.port &&
                maxSessions == that.maxSessions &&
                maxQueryResultSize == that.maxQueryResultSize &&
                Objects.equals(host, that.host) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(database, that.database) &&
                Objects.equals(tablePrefix, that.tablePrefix) &&
                Objects.equals(retention, that.retention) &&
                Objects.equals(retentionOverrides, that.retentionOverrides) &&
                Objects.equals(queryTimeout, that.queryTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, username, password, database, tablePrefix, maxSessions, retention,
                retentionOverrides, maxQueryResultSize, queryTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "host=" + host +
                ", port=" + port +
                ", username=" + username +
                ", password=***" +
                ", database=" + database +
                ", tablePrefix=" + tablePrefix +
                ", maxSessions=" + maxSessions +
                ", retention=" + retention +
                ", retentionOverrides=" + retentionOverrides +
                ", maxQueryResultSize=" + maxQueryResultSize +
                ", queryTimeout=" + queryTimeout +
                "]";
    }
}
