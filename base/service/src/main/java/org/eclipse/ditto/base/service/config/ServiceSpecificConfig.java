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
package org.eclipse.ditto.base.service.config;

import org.eclipse.ditto.base.service.config.http.HttpConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.cluster.config.WithClusterConfig;
import org.eclipse.ditto.internal.utils.metrics.config.WithMetricsConfig;
import org.eclipse.ditto.internal.utils.tracing.config.WithTracingConfig;

/**
 * Provides the common configuration settings of each Ditto service.
 * This interface is the base of all service specific configuration settings.
 */
public interface ServiceSpecificConfig extends WithClusterConfig, WithMetricsConfig, WithTracingConfig {

    /**
     * Returns the limits config.
     *
     * @return the limits config.
     */
    LimitsConfig getLimitsConfig();

    /**
     * Returns the HTTP config.
     *
     * @return the HTTP config.
     */
    HttpConfig getHttpConfig();

}
