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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer} and guarantees that no
 * timer will run without a timeout, because the timer is always returned started.
 */
public final class ExpiringTimerBuilder implements TimerBuilder<ExpiringTimerBuilder, StartedTimer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTimerBuilder.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final String name;
    private final Map<String, String> additionalTags;

    private long maximumDuration = 5;
    private TimeUnit maximumDurationTimeUnit = TimeUnit.MINUTES;
    private Consumer<StartedTimer> additionalExpirationHandling;
    private ScheduledFuture<?> expirationHandlingFuture;


    public ExpiringTimerBuilder(final String name) {
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
    public ExpiringTimerBuilder tags(final Map<String, String> additionalTags) {
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
    public ExpiringTimerBuilder tag(final String key, final String value) {
        this.additionalTags.put(key, value);
        return this;
    }

    /**
     * Sets the handling of a timer after expiration.
     *
     * @param additionalExpirationHandling custom handling of timer expiration.
     * @return The TracingTimerBuilder
     */
    public ExpiringTimerBuilder expirationHandling(final Consumer<StartedTimer> additionalExpirationHandling) {
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
    public ExpiringTimerBuilder maximumDuration(final long maximumDuration,
            final TimeUnit maximumDurationTimeUnit) {
        this.maximumDuration = maximumDuration;
        this.maximumDurationTimeUnit = maximumDurationTimeUnit;
        return this;
    }

    /**
     * Starts the timer.
     *
     * @return The timer that will be stopped after running more than the defined
     * {@link ExpiringTimerBuilder#maximumDuration maximum duration}
     */
    @Override
    public StartedTimer build() {
        final StartedTimer timer = PreparedKamonTimer.newTimer(name).tags(additionalTags).start();
        expirationHandlingFuture =
                scheduler.schedule(() -> defaultExpirationHandling(name, timer, additionalExpirationHandling),
                        maximumDuration, maximumDurationTimeUnit);
        timer.onStop(new OnStopHandler(this::cancelScheduledExpirationFuture));
        return timer;
    }

    private void cancelScheduledExpirationFuture(final StoppedTimer timer) {

        if (!expirationHandlingFuture.isDone()) {
            final boolean canceled = expirationHandlingFuture.cancel(false);
            if (canceled) {
                LOGGER.debug("Canceled expiration handling of MutableKamonTimer <{}> because it has been stopped " +
                        "before timeout", timer.getName());
            }
        }
    }

    private static void defaultExpirationHandling(final String tracingFilter, final StartedTimer timer,
            @Nullable Consumer<StartedTimer> additionalExpirationHandling) {
        LOGGER.debug("Trace for {} stopped. Cause: Timer expired", tracingFilter);

        if (additionalExpirationHandling != null) {
            try {
                additionalExpirationHandling.accept(timer);
            } finally {
                if (timer.isRunning()) {
                    timer.stop();
                }
            }
        } else {
            if (timer.isRunning()) {
                timer.stop();
            }
        }
    }
}
