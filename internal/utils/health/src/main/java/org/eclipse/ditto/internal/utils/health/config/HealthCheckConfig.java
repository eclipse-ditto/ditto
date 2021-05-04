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
package org.eclipse.ditto.internal.utils.health.config;

import javax.annotation.concurrent.Immutable;

/**
 * Provides configuration settings regarded to Ditto's runtime health.
 */
@Immutable
public interface HealthCheckConfig extends BasicHealthCheckConfig, WithPersistenceConfig {
}
