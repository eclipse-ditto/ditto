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

import java.time.Duration;

/**
 * Configuration for the dynamic config file watcher.
 */
public interface DynamicConfigWatcherConfig {

    /**
     * @return whether dynamic config watching is enabled.
     */
    boolean isEnabled();

    /**
     * @return the path to the dynamic config file on disk.
     */
    String getFilePath();

    /**
     * @return the interval at which the config file is polled for changes.
     */
    Duration getPollInterval();
}
