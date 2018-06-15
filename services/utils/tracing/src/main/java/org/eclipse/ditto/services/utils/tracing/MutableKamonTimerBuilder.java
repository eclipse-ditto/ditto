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
package org.eclipse.ditto.services.utils.tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a MutableKamonTimer and guarantees that no timer will run without a timeout, because the timer is always
 * returned started.
 */
public final class MutableKamonTimerBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(MutableKamonTimerBuilder.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final String tracingFilter;
    private final Map<String, String> additionalTags = new HashMap<>();

    private long maximumDuration = 5;
    private TimeUnit maximumDurationTimeUnit = TimeUnit.MINUTES;
    private Consumer<MutableKamonTimer> additionalExpirationHandling;


    private MutableKamonTimerBuilder(final String tracingFilter) {
        this.tracingFilter = tracingFilter;
    }

    static MutableKamonTimerBuilder newTimer(final String tracingFilter) {
        return new MutableKamonTimerBuilder(tracingFilter);
    }

    /**
     * Adds tags to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param additionalTags Additional tags for this tracing
     * @return The TracingTimerBuilder
     */
    public MutableKamonTimerBuilder tags(final Map<String, String> additionalTags) {
        this.additionalTags.putAll(additionalTags);
        return this;
    }

    /**
     * Sets the handling of a timer after expiration.
     *
     * @param additionalExpirationHandling custom handling of timer expiration.
     * @return The TracingTimerBuilder
     */
    public MutableKamonTimerBuilder expirationHandling(final Consumer<MutableKamonTimer> additionalExpirationHandling) {
        this.additionalExpirationHandling = additionalExpirationHandling;
        return this;
    }

    /**
     * Specifies the maximum duration this timer should be running. It will expire after this time.
     *
     * @param maximumDuration The maximum duration.
     * @param maximumDurationTimeUnit The unit of the maximum duration.
     * @return The TracingTimerBuilder
     */
    public MutableKamonTimerBuilder maximumDuration(final long maximumDuration,
            final TimeUnit maximumDurationTimeUnit) {
        this.maximumDuration = maximumDuration;
        this.maximumDurationTimeUnit = maximumDurationTimeUnit;
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
    public MutableKamonTimerBuilder tag(final String key, final String value) {
        this.additionalTags.put(key, value);
        return this;
    }

    /**
     * Starts the timer.
     *
     * @return The timer that will be stopped after running more than the defined
     * {@link MutableKamonTimerBuilder#maximumDuration maximum duration}
     */
    public MutableKamonTimer buildStartedTimer() {
        final MutableKamonTimer timer = new MutableKamonTimer(tracingFilter).tags(additionalTags);
        scheduler.schedule(() -> defaultExpirationHandling(tracingFilter, timer, additionalExpirationHandling),
                maximumDuration, maximumDurationTimeUnit);
        return timer.start();
    }

    private static void defaultExpirationHandling(final String tracingFilter, final MutableKamonTimer timer,
            @Nullable Consumer<MutableKamonTimer> additionalExpirationHandling) {
        timer.stop();

        LOGGER.debug("Trace for {} stopped. Cause: Timer expired", tracingFilter);

        if (additionalExpirationHandling != null) {
            additionalExpirationHandling.accept(timer);
        }
    }
}
