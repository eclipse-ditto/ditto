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
package org.eclipse.ditto.concierge.service.common;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.internal.utils.health.config.WithHealthCheckConfig;

/**
 * Provides the configuration settings of the Concierge service.
 */
@Immutable
public interface ConciergeConfig extends ServiceSpecificConfig, WithHealthCheckConfig {

    /**
     * Returns the config of Concierge's enforcement behaviour.
     *
     * @return the config.
     */
    EnforcementConfig getEnforcementConfig();

    /**
     * Returns the config of Concierge's caches.
     *
     * @return the config.
     */
    CachesConfig getCachesConfig();

    /**
     * Returns the config of Concierge's things aggregation.
     *
     * @return the config.
     */
    ThingsAggregatorConfig getThingsAggregatorConfig();

}
