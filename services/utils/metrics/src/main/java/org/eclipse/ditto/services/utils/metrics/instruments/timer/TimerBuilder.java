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
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import java.util.Map;

/**
 * Builder for {@link Timer}s.
 *
 * @param <T> the type of the TimerBuilder itself
 * @param <B> the type of the Timer to build
 */
public interface TimerBuilder<T extends TimerBuilder, B extends Timer> {

    /**
     * Adds tags to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param additionalTags Additional tags for this tracing.
     * @return The TracingTimerBuilder.
     */
    T tags(Map<String, String> additionalTags);

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key They key of the tag.
     * @param value The value of the tag.
     * @return The TracingTimerBuilder.
     */
    T tag(String key, String value);

    /**
     * Builds the timer.
     *
     * @return the built timer.
     */
    B build();
}
