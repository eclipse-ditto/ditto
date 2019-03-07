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
package org.eclipse.ditto.services.policies.persistence.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.WithSupervisorConfig;

/**
 * Provides configuration settings for policy entities.
 * <p>
 * Java serialization is supported for {@code PolicyConfig}.
 * </p>
 */
@Immutable
public interface PolicyConfig extends WithSupervisorConfig {

    /**
     * Returns the configuration settings of the activity check.
     *
     * @return the config.
     */
    ActivityCheckConfig getActivityCheckConfig();

    /**
     * Returns the configuration settings for the handling of policy entity snapshots.
     *
     * @return the config.
     */
    SnapshotConfig getSnapshotConfig();

}
