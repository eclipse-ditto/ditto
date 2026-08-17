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
package org.eclipse.ditto.timeseries.api;

/**
 * Marker interface for timeseries-adapter configuration.
 * <p>
 * Concrete adapters define their own configuration types (e.g. a MongoDB adapter exposes
 * connection-string and granularity, an IoTDB adapter exposes host/port). The Timeseries service
 * loads the appropriate config according to {@code ditto.timeseries.adapter.type} and passes the
 * resolved instance to {@link TimeseriesAdapter#initialize(TimeseriesAdapterConfig)}.
 * <p>
 * No methods are declared on this interface yet; it exists to make the SPI contract explicit and
 * to give a single type-token for adapter-config classes to share.
 *
 * @since 4.0.0
 */
public interface TimeseriesAdapterConfig {
}
