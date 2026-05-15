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

import javax.annotation.concurrent.Immutable;

/**
 * Health status of a timeseries adapter as exposed by
 * {@link TimeseriesAdapter#getHealth()}.
 *
 * @since 4.0.0
 */
@Immutable
public enum HealthStatus {

    /**
     * The adapter is fully operational.
     */
    UP,

    /**
     * The adapter is reachable but operating in a degraded mode (e.g. only some replicas
     * available, write-through to backup disabled).
     */
    DEGRADED,

    /**
     * The adapter is not currently usable.
     */
    DOWN
}
