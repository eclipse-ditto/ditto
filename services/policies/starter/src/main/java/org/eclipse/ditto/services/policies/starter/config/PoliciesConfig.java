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
package org.eclipse.ditto.services.policies.starter.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.policies.persistence.config.PolicyConfig;
import org.eclipse.ditto.services.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

/**
 * Provides the configuration settings of the Policies service.
 * <p>
 * Java serialization is supported for {@code PoliciesConfig}.
 * </p>
 */
@Immutable
public interface PoliciesConfig extends ServiceSpecificConfig, WithHealthCheckConfig, WithMongoDbConfig {

    /**
     * Returns the configuration settings for policy entities.
     *
     * @return the config.
     */
    PolicyConfig getPolicyConfig();

    /**
     * Returns the configuration settings for policies tags.
     *
     * @return the config.
     */
    TagsConfig getTagsConfig();

}
