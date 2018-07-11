/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a {@link org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer} and guarantees that no
 * timer will run without a timeout, because the timer is always returned started.
 */
public final class DefaultTimerBuilder implements TimerBuilder<DefaultTimerBuilder, PreparedTimer> {

    private final String name;
    private final Map<String, String> additionalTags;

    public DefaultTimerBuilder(final String name) {
        this.name = name;
        this.additionalTags = new HashMap<>();
    }

    /**
     * Adds tags to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param additionalTags Additional tags for this tracing
     * @return The TracingTimerBuilder
     */
    @Override
    public DefaultTimerBuilder tags(final Map<String, String> additionalTags) {
        this.additionalTags.putAll(additionalTags);
        return this;
    }

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key They key of the tag
     * @param value The value of the tag
     * @return The TracingTimerBuilder
     */
    public DefaultTimerBuilder tag(final String key, final String value) {
        this.additionalTags.put(key, value);
        return this;
    }

    /**
     * builds the timer.
     *
     * @return The timer
     */
    @Override
    public PreparedTimer build() {
        return PreparedKamonTimer.newTimer(name).tags(additionalTags);
    }
}
