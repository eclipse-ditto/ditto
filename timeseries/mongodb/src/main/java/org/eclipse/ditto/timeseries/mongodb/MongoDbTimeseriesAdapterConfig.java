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
import java.util.Optional;

import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;

/**
 * Configuration for the MongoDB Time Series adapter — the default Ditto-shipped implementation of
 * {@link org.eclipse.ditto.timeseries.api.TimeseriesAdapter}.
 */
public interface MongoDbTimeseriesAdapterConfig extends TimeseriesAdapterConfig {

    /**
     * @return the MongoDB connection URI (e.g. {@code mongodb://localhost:27017}).
     */
    String getUri();

    /**
     * @return the MongoDB database name in which timeseries collections are created
     * (default {@code "ditto_ts"}).
     */
    String getDatabase();

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
     * @return the retention applied to new MongoDB Time Series collections, mapped to
     * {@code TimeSeriesOptions.expireAfter(seconds)}. Empty disables expiration — documents are
     * kept until manually purged. Existing collections are <em>not</em> reconfigured when this
     * value changes; the {@code collMod} migration is operator-driven.
     */
    Optional<Duration> getRetention();
}
