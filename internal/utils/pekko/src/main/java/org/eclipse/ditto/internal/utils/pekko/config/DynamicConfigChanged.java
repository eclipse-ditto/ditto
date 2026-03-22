/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pekko.config;

import java.time.Instant;

import com.typesafe.config.Config;

/**
 * Event published on the Pekko EventStream when the dynamic configuration file has changed.
 *
 * @param dittoConfig the new merged {@code ditto.*} scoped config.
 * @param previousDittoConfig the previous {@code ditto.*} scoped config.
 * @param version the monotonically increasing config version.
 * @param timestamp the instant when the change was detected.
 */
public record DynamicConfigChanged(Config dittoConfig, Config previousDittoConfig,
                                   long version, Instant timestamp) {
}
