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
package org.eclipse.ditto.things.service.common.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.WithSupervisorConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.WithCleanupConfig;

/**
 * Provides configuration settings for thing entities.
 */
@Immutable
public interface ThingConfig extends WithSupervisorConfig, WithActivityCheckConfig, WithSnapshotConfig,
        WithCleanupConfig {
}
