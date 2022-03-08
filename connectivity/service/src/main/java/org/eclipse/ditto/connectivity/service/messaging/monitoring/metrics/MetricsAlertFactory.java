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

import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;

/**
 * Factory interface for {@code MetricsAlert}s.
 */
@FunctionalInterface
public interface MetricsAlertFactory {

    /**
     * Creates a new instance of a metrics alert.
     *
     * @param counterKey the key that identifies a counter.
     * @param connectionType the connection type.
     * @param connectivityConfig the connectivity config.
     * @param isGauge whether the alert is a gauge or not.
     * @return the new metrics alert.
     */
    MetricsAlert create(final CounterKey counterKey, final ConnectionType connectionType,
            final ConnectivityConfig connectivityConfig, final boolean isGauge);
}
