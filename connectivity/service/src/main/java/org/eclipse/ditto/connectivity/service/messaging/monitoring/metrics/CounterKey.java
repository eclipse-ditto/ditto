/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;

/**
 * Identifies a counter by connection id, metric direction, metric type and address.
 */
public final class CounterKey {

    private final ConnectionId connectionId;
    private final String address;

    @Nullable private final MetricType metricType;
    @Nullable private final MetricDirection metricDirection;

    private CounterKey(final ConnectionId connectionId,
            final String address,
            @Nullable final MetricType metricType,
            @Nullable final MetricDirection metricDirection) {

        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.address = checkNotNull(address, "address");

        this.metricType = metricType;
        this.metricDirection = metricDirection;
    }

    /**
     * Returns a new {@code CounterKey}.
     *
     * @param connectionId the connection id of the counter.
     * @param address the address of the counter.
     * @return the CounterKey.
     */
    public static CounterKey of(final ConnectionId connectionId, final String address) {

        return new CounterKey(connectionId, address, null, null);
    }

    /**
     *
     * Returns a new {@code CounterKey}.
     *
     * @param connectionId the connection id of the counter.
     * @param address the address of the counter.
     * @param metricType the metric type of the counter.
     * @param metricDirection the metric direction of the counter.
     * @return the CounterKey.
     */
    public static CounterKey of(final ConnectionId connectionId,
            final String address,
            final MetricType metricType,
            final MetricDirection metricDirection) {
        checkNotNull(metricType, "metricType");
        checkNotNull(metricDirection, "metricDirection");
        return new CounterKey(connectionId, address, metricType, metricDirection);
    }

    ConnectionId getConnectionId() {
        return connectionId;
    }

    Optional<MetricType> getMetricType() {
        return Optional.ofNullable(metricType);
    }

    Optional<MetricDirection> getMetricDirection() {
        return Optional.ofNullable(metricDirection);
    }

    String getAddress() {
        return address;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CounterKey counterKey = (CounterKey) o;
        return Objects.equals(connectionId, counterKey.connectionId) &&
                Objects.equals(metricType, counterKey.metricType) &&
                Objects.equals(metricDirection, counterKey.metricDirection) &&
                Objects.equals(address, counterKey.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, metricType, metricDirection, address);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", metricType=" + metricType +
                ", metricDirection=" + metricDirection +
                ", address=" + address +
                "]";
    }

}
