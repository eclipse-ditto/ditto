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
package org.eclipse.ditto.services.concierge.starter.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.concierge.cache.config.CachesConfig;
import org.eclipse.ditto.services.concierge.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.services.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

/**
 * Provides the configuration settings of the Concierge service.
 * <p>
 * Java serialization is supported for {@code ConciergeConfig}.
 * </p>
 */
@Immutable
public interface ConciergeConfig extends ServiceSpecificConfig, WithHealthCheckConfig, WithMongoDbConfig {

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
