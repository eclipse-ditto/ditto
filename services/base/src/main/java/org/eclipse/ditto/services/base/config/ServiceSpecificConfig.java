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
package org.eclipse.ditto.services.base.config;

import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.utils.cluster.config.WithClusterConfig;
import org.eclipse.ditto.services.utils.metrics.config.WithMetricsConfig;

/**
 * Provides the common configuration settings of each Ditto service.
 * This interface is the base of all service specific configuration settings.
 */
public interface ServiceSpecificConfig extends WithClusterConfig, WithMetricsConfig {

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
