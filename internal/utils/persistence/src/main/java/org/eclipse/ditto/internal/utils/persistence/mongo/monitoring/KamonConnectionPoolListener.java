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
package org.eclipse.ditto.internal.utils.persistence.mongo.monitoring;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.connection.ServerId;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionClosedEvent;
import com.mongodb.event.ConnectionCreatedEvent;
import com.mongodb.event.ConnectionPoolClosedEvent;
import com.mongodb.event.ConnectionPoolCreatedEvent;
import com.mongodb.event.ConnectionPoolListener;

/**
 * Reports MongoDB connection pool statistics to Kamon.
 */
public class KamonConnectionPoolListener implements ConnectionPoolListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonConnectionPoolListener.class);
    private final ConcurrentMap<ServerId, PoolMetric> metrics = new ConcurrentHashMap<>();
    private final String metricName;

    public KamonConnectionPoolListener(final String metricName) {
        this.metricName = metricName;
    }

    @Override
    public void connectionPoolCreated(final ConnectionPoolCreatedEvent event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Connection pool opened: {}", event);
        }
        final PoolMetric metric = new PoolMetric(event.getServerId());
        metrics.put(event.getServerId(), metric);
    }

    @Override
    public void connectionPoolClosed(final ConnectionPoolClosedEvent event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Connection pool closed: {}", event);
        }
        metrics.remove(event.getServerId());
    }

    @Override
    public void connectionCheckedOut(final ConnectionCheckedOutEvent event) {
        metrics.compute(event.getConnectionId().getServerId(), (serverId, metric) -> metric.incCheckedOutCount());
    }

    @Override
    public void connectionCheckedIn(final ConnectionCheckedInEvent event) {
        metrics.compute(event.getConnectionId().getServerId(), (serverId, metric) -> metric.decCheckedOutCount());

    }

    @Override
    public void connectionCreated(final ConnectionCreatedEvent event) {
        metrics.compute(event.getConnectionId().getServerId(), (serverId, metric) -> metric.incPoolSize());
    }

    @Override
    public void connectionClosed(final ConnectionClosedEvent event) {
        metrics.compute(event.getConnectionId().getServerId(), (serverId, metric) -> metric.decPoolSize());

    }

    private class PoolMetric {

        private static final String POOL_PREFIX = "_pool";
        private static final String CHECKED_OUT_COUNT = "_checkedOutCount";
        private static final String POOL_SIZE = "_poolSize";
        private static final String CLUSTER_ID_TAG = "cluster_id";

        private final Gauge poolSizeGauge;
        private final Gauge checkOutCountGauge;

        private PoolMetric(final ServerId serverId) {
            final String clusterId = serverId.getClusterId().getValue();
            poolSizeGauge = DittoMetrics.gauge(metricName + POOL_PREFIX + POOL_SIZE)
                    .tag(CLUSTER_ID_TAG, clusterId);
            poolSizeGauge.set(0L);

            checkOutCountGauge = DittoMetrics.gauge(metricName + POOL_PREFIX + CHECKED_OUT_COUNT)
                    .tag(CLUSTER_ID_TAG, clusterId);
            checkOutCountGauge.set(0L);
        }

        private PoolMetric incPoolSize() {
            poolSizeGauge.increment();
            return this;
        }

        private PoolMetric decPoolSize() {
            poolSizeGauge.decrement();
            return this;
        }

        private PoolMetric incCheckedOutCount() {
            checkOutCountGauge.increment();
            return this;
        }

        private PoolMetric decCheckedOutCount() {
            checkOutCountGauge.decrement();
            return this;
        }

    }
}
