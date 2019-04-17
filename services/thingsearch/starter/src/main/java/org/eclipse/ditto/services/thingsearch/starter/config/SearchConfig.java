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
package org.eclipse.ditto.services.thingsearch.starter.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.thingsearch.updater.config.DeletionConfig;
import org.eclipse.ditto.services.thingsearch.updater.config.UpdaterConfig;
import org.eclipse.ditto.services.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithIndexInitializationConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

/**
 * Provides the configuration settings of the Search service.
 * <p>
 * Java serialization is supported for SearchConfig.
 * </p>
 */
@Immutable
public interface SearchConfig
        extends ServiceSpecificConfig, WithHealthCheckConfig, WithMongoDbConfig, WithIndexInitializationConfig {

    /**
     * Returns the configuration settings for the physical deletion of thing entities that are marked as
     * {@code "__deleted"}.
     *
     * @return the config.
     */
    DeletionConfig getDeletionConfig();

    /**
     * Returns the configuration settings for the search updating functionality.
     *
     * @return the config.
     */
    UpdaterConfig getUpdaterConfig();

}
