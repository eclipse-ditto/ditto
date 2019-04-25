/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base.config;

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
