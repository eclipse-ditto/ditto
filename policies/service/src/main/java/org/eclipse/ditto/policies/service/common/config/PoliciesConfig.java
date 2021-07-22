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
package org.eclipse.ditto.policies.service.common.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.internal.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.WithPersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig;

/**
 * Provides the configuration settings of the Policies service.
 */
@Immutable
public interface PoliciesConfig extends ServiceSpecificConfig, WithHealthCheckConfig, WithPersistenceOperationsConfig,
        WithMongoDbConfig {

    /**
     * Returns the configuration settings for policy entities.
     *
     * @return the config.
     */
    PolicyConfig getPolicyConfig();

    /**
     * Returns the config for Policy service's Policy wakeup behaviour. Some Policies must always be kept alive in
     * memory.
     *
     * @return the config.
     */
    PingConfig getPingConfig();

}
