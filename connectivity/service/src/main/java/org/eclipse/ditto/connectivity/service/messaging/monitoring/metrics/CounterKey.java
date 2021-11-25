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

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;

/**
 * Identifies a counter by connection id, metric direction, metric type and address.
 */
final class CounterKey {

    private final ConnectionId connectionId;
    private final MetricType metricType;
    private final MetricDirection metricDirection;
    private final String address;

    /**
     * New map key.
     *
     * @param connectionId connection id
     * @param metricType the metricType
     * @param metricDirection the metricDirection
     * @param address the address
     */
    private CounterKey(final ConnectionId connectionId, final MetricType metricType,
            final MetricDirection metricDirection, final String address) {
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.metricType = checkNotNull(metricType, "metricType");
        this.metricDirection = checkNotNull(metricDirection, "metricDirection");
        this.address = checkNotNull(address, "address");
    }

    static CounterKey of(final ConnectionId connectionId, final MetricType metricType,
            final MetricDirection metricDirection, final String address) {
        return new CounterKey(connectionId, metricType, metricDirection, address);
    }

    ConnectionId getConnectionId() {
        return connectionId;
    }

    MetricType getMetricType() {
        return metricType;
    }

    MetricDirection getMetricDirection() {
        return metricDirection;
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
