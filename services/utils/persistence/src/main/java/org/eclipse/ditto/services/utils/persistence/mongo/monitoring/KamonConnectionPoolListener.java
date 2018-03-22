/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.utils.persistence.mongo.monitoring;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.connection.ServerId;
import com.mongodb.event.ConnectionAddedEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionPoolClosedEvent;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.event.ConnectionPoolOpenedEvent;
import com.mongodb.event.ConnectionPoolWaitQueueEnteredEvent;
import com.mongodb.event.ConnectionPoolWaitQueueExitedEvent;
import com.mongodb.event.ConnectionRemovedEvent;

import kamon.Kamon;
import kamon.metric.instrument.Gauge;

/**
 * Reports MongoDB connection pool statistics to Kamon.
 */
public class KamonConnectionPoolListener implements ConnectionPoolListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonCommandListener.class);
    private final ConcurrentMap<ServerId, PoolMetric> metrics = new ConcurrentHashMap<>();
    private final String metricName;

    public KamonConnectionPoolListener(final String metricName) {
        this.metricName = metricName;
    }

    @Override
    public void connectionPoolOpened(final ConnectionPoolOpenedEvent event) {
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
        final PoolMetric metric = metrics.remove(event.getServerId());
        if (metric != null) {
            metric.remove();
        }
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
    public void waitQueueEntered(final ConnectionPoolWaitQueueEnteredEvent event) {
        metrics.compute(event.getServerId(), (serverId, metric) -> metric.incWaitQueueSize());
    }

    @Override
    public void waitQueueExited(final ConnectionPoolWaitQueueExitedEvent event) {
        metrics.compute(event.getServerId(), (serverId, metric) -> metric.decWaitQueueSize());
    }

    @Override
    public void connectionAdded(final ConnectionAddedEvent event) {
        metrics.compute(event.getConnectionId().getServerId(), (serverId, metric) -> metric.incPoolSize());
    }

    @Override
    public void connectionRemoved(final ConnectionRemovedEvent event) {
        metrics.compute(event.getConnectionId().getServerId(), (serverId, metric) -> metric.decPoolSize());

    }

    private class PoolMetric {

        private static final String POOL_PREFIX = ".pool.";
        private static final String CHECKED_OUT_COUNT = ".checkedOutCount";
        private static final String POOL_SIZE = ".poolSize";
        private static final String WAIT_QUEUE_SIZE = ".waitQueueSize";

        private final AtomicLong poolSize = new AtomicLong();
        private final AtomicLong checkedOutCount = new AtomicLong();
        private final AtomicLong waitQueueSize = new AtomicLong();
        private final String name;
        private final Gauge poolSizeGauge;
        private final Gauge checkOutCountGauge;
        private final Gauge waitQueueGauge;

        private PoolMetric(final ServerId serverId) {
            this.name = serverId.getClusterId().getValue();
            poolSizeGauge = Kamon.metrics().gauge(metricName + POOL_PREFIX + name + POOL_SIZE, poolSize::get);
            checkOutCountGauge =
                    Kamon.metrics().gauge(metricName + POOL_PREFIX + name + CHECKED_OUT_COUNT, checkedOutCount::get);
            waitQueueGauge =
                    Kamon.metrics().gauge(metricName + POOL_PREFIX + name + WAIT_QUEUE_SIZE, waitQueueSize::get);
        }

        private PoolMetric incPoolSize() {
            poolSizeGauge.record(poolSize.incrementAndGet());
            return this;
        }

        private PoolMetric decPoolSize() {
            poolSizeGauge.record(poolSize.decrementAndGet());
            return this;
        }

        private PoolMetric incCheckedOutCount() {
            checkOutCountGauge.record(checkedOutCount.incrementAndGet());
            return this;
        }

        private PoolMetric decCheckedOutCount() {
            checkOutCountGauge.record(checkedOutCount.decrementAndGet());
            return this;
        }

        private PoolMetric incWaitQueueSize() {
            waitQueueGauge.record(waitQueueSize.incrementAndGet());
            return this;
        }

        private PoolMetric decWaitQueueSize() {
            waitQueueGauge.record(waitQueueSize.decrementAndGet());
            return this;
        }

        private void remove() {
            Kamon.metrics().removeGauge(metricName + POOL_PREFIX + name + POOL_SIZE);
            Kamon.metrics().removeGauge(metricName + POOL_PREFIX + name + CHECKED_OUT_COUNT);
            Kamon.metrics().removeGauge(metricName + POOL_PREFIX + name + WAIT_QUEUE_SIZE);
        }
    }
}
