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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a MutableKamonTimer and guarantees that no timer will run without a timeout, because the timer is always
 * returned started.
 */
public final class KamonTimerBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonTimerBuilder.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final String tracingFilter;
    private final Map<String, String> additionalTags = new HashMap<>();

    private long maximumDuration = 5;
    private TimeUnit maximumDurationTimeUnit = TimeUnit.MINUTES;
    private Consumer<KamonTimer> additionalExpirationHandling;
    private ScheduledFuture<?> expirationHandlingFuture;


    private KamonTimerBuilder(final String tracingFilter) {
        this.tracingFilter = tracingFilter;
    }

    static KamonTimerBuilder newTimer(final String tracingFilter) {
        return new KamonTimerBuilder(tracingFilter);
    }

    /**
     * Adds tags to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param additionalTags Additional tags for this tracing
     * @return The TracingTimerBuilder
     */
    public KamonTimerBuilder tags(final Map<String, String> additionalTags) {
        this.additionalTags.putAll(additionalTags);
        return this;
    }

    /**
     * Sets the handling of a timer after expiration.
     *
     * @param additionalExpirationHandling custom handling of timer expiration.
     * @return The TracingTimerBuilder
     */
    public KamonTimerBuilder expirationHandling(final Consumer<KamonTimer> additionalExpirationHandling) {
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
    public KamonTimerBuilder maximumDuration(final long maximumDuration,
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
    public KamonTimerBuilder tag(final String key, final String value) {
        this.additionalTags.put(key, value);
        return this;
    }

    /**
     * Starts the timer.
     *
     * @return The timer that will be stopped after running more than the defined
     * {@link KamonTimerBuilder#maximumDuration maximum duration}
     */
    public KamonTimer buildStartedTimer() {
        final KamonTimer timer = new KamonTimer(tracingFilter).tags(additionalTags);
        expirationHandlingFuture =
                scheduler.schedule(() -> defaultExpirationHandling(tracingFilter, timer, additionalExpirationHandling),
                        maximumDuration, maximumDurationTimeUnit);
        timer.onStop(this::cancelScheduledExpirationFuture);
        return timer.start();
    }

    private void cancelScheduledExpirationFuture(final KamonTimer timer) {

        if (!expirationHandlingFuture.isDone()) {
            final boolean canceled = expirationHandlingFuture.cancel(false);
            if (canceled) {
                LOGGER.debug("Canceled expiration handling of MutableKamonTimer <{}> because it has been stopped " +
                        "before timeout", timer.getName());
            }
        }
    }

    private static void defaultExpirationHandling(final String tracingFilter, final KamonTimer timer,
            @Nullable Consumer<KamonTimer> additionalExpirationHandling) {
        LOGGER.debug("Trace for {} stopped. Cause: Timer expired", tracingFilter);

        if (additionalExpirationHandling != null) {
            try {
                additionalExpirationHandling.accept(timer);
            } catch (Exception e) {
                timer.stop();
                LOGGER.warn("Expiration Handling for timer <{}> caused an unexpected exception", timer.getName(), e);
            }
        }
    }
}
