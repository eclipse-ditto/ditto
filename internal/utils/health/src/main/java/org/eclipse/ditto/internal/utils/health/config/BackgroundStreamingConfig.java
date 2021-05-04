/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;

import com.typesafe.config.Config;

/**
 * Configuration for background streaming behavior.
 */
public interface BackgroundStreamingConfig {

    /**
     * Returns whether background streaming is turned on.
     *
     * @return true or false.
     */
    boolean isEnabled();

    /**
     * Returns how long to wait between streams.
     *
     * @return duration of the quiet period.
     */
    Duration getQuietPeriod();

    /**
     * Returns how many events to keep in the actor state.
     *
     * @return number of kept events.
     */
    int getKeptEvents();

    /**
     * Return the config in HOCON format.
     *
     * @return the HOCON.
     */
    Config getConfig();
}
