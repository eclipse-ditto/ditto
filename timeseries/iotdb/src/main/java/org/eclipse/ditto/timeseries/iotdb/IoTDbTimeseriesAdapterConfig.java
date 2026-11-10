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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;


/**
 * Configuration of the {@link IoTDbTimeseriesAdapter}, loaded from {@code ditto.timeseries.adapter.iotdb}.
 *
 * @since 4.0.0
 */
public interface IoTDbTimeseriesAdapterConfig {

    /**
     * @return the host of the IoTDB node to connect to.
     */
    String getHost();

    /**
     * @return the RPC port of the IoTDB node.
     */
    int getPort();

    /**
     * @return the IoTDB user name.
     */
    String getUsername();

    /**
     * @return the IoTDB password.
     */
    String getPassword();

    /**
     * @return the IoTDB database holding one table per Thing namespace; created on start-up if absent.
     */
    String getDatabase();

    /**
     * @return the prefix of the per-namespace table names.
     */
    String getTablePrefix();

    /**
     * @return the maximum number of concurrent IoTDB sessions, which is also the number of threads blocking on them.
     */
    int getMaxSessions();

    /**
     * @return the default retention applied as table TTL, or empty for no expiration.
     */
    Optional<Duration> getRetention();

    /**
     * @return per-namespace retention overrides, keyed by Thing namespace.
     */
    Map<String, Duration> getRetentionOverrides();

    /**
     * Returns the retention of the given namespace: its override if configured, else the default.
     *
     * @param namespace the Thing namespace.
     * @return the retention, or empty for no expiration.
     */
    default Optional<Duration> getRetention(final CharSequence namespace) {
        final Duration override = getRetentionOverrides().get(String.valueOf(namespace));
        return override != null ? Optional.of(override) : getRetention();
    }

    /**
     * @return the maximum number of data points read into memory per path.
     */
    int getMaxQueryResultSize();

    /**
     * @return the time budget of a single IoTDB statement, enforced server-side and on the returned future.
     */
    Duration getQueryTimeout();
}
