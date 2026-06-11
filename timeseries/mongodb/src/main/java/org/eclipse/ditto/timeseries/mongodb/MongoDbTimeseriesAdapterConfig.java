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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;

/**
 * Configuration for the MongoDB Time Series adapter — the default Ditto-shipped implementation of
 * {@link org.eclipse.ditto.timeseries.api.TimeseriesAdapter}.
 * <p>
 * The connection itself (URI, IAM auth, pool sizing, SSL) is delegated to {@link MongoDbConfig}
 * via {@link #getMongoDbConfig()} — same code path as Ditto's sibling services (things, policies,
 * connectivity, …) which feed the {@code pekko-persistence-mongodb} plugin from the same source.
 * Only the timeseries-specific tuning (database name lives on {@code MongoDbConfig#getMongoDbDatabaseName()},
 * collection prefix, granularity, retention) is surfaced here.
 */
public interface MongoDbTimeseriesAdapterConfig extends TimeseriesAdapterConfig {

    /**
     * @return the shared Ditto MongoDB connection configuration. Carries the URI, IAM settings,
     * pool / circuit-breaker / SSL options and is consumed by {@code MongoClientWrapper.newInstance(…)}
     * to build the actual MongoDB client.
     */
    MongoDbConfig getMongoDbConfig();

    /**
     * @return the prefix used when deriving a per-namespace collection name
     * (default {@code "ts_"}).
     */
    String getCollectionPrefix();

    /**
     * @return the {@link Granularity} used for new MongoDB Time Series collections.
     */
    Granularity getGranularity();

    /**
     * @return the default retention applied to MongoDB Time Series collections, mapped to
     * {@code TimeSeriesOptions.expireAfter(seconds)}. Empty disables expiration — documents are
     * kept until manually purged. The adapter reconciles existing collections to this value on
     * startup via {@code collMod}, so a changed value takes effect without manual migration.
     * Per-namespace overrides take precedence; see {@link #getRetentionOverrides()} and
     * {@link #getRetention(CharSequence)}.
     */
    Optional<Duration> getRetention();

    /**
     * @return per-namespace retention overrides, keyed by Thing namespace (e.g.
     * {@code "com.acme.hifreq"}). A namespace present here uses its own retention instead of the
     * default from {@link #getRetention()}; absent namespaces fall back to the default. Each value
     * is a positive duration. Never {@code null}; may be empty.
     */
    Map<String, Duration> getRetentionOverrides();

    /**
     * Resolves the effective retention for a Thing namespace: the per-namespace override from
     * {@link #getRetentionOverrides()} if present, otherwise the default {@link #getRetention()}.
     *
     * @param namespace the Thing namespace (the part of the collection identity that selects an
     * override).
     * @return the effective retention, or empty when neither an override nor a default is set
     * (i.e. expiration disabled for that namespace).
     */
    default Optional<Duration> getRetention(final CharSequence namespace) {
        final Duration override = getRetentionOverrides().get(String.valueOf(namespace));
        return override != null ? Optional.of(override) : getRetention();
    }

    /**
     * @return the maximum number of data points pulled into application memory per path on the raw
     * and window-function read paths. Acts as a safety ceiling so an over-broad time range cannot
     * exhaust the heap; results that hit it are truncated (and logged). Callers should narrow the
     * range, add a {@code limit}, or downsample with a {@code step}.
     */
    int getMaxQueryResultSize();

    /**
     * @return the server-side time budget for a single read (find or aggregation), applied as the
     * MongoDB {@code maxTime}. Bounds the blast radius of a pathological query.
     */
    Duration getQueryTimeout();
}
