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
package org.eclipse.ditto.services.things.persistence.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.WithSupervisorConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithSnapshotConfig;

/**
 * Provides configuration settings for thing entities.
 * <p>
 * Java serialization is supported for {@code ThingConfig}.
 * </p>
 */
@Immutable
public interface ThingConfig extends WithSupervisorConfig, WithActivityCheckConfig, WithSnapshotConfig {
}
