/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.config;

/**
 * This interface provides access to the {@link MetricsConfig}.
 */
public interface WithMetricsConfig {

    /**
     * Returns the metrics config.
     *
     * @return the config.
     */
    MetricsConfig getMetricsConfig();

}
