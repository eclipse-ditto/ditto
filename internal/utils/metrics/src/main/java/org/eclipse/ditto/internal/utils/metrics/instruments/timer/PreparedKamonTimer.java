/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.Distribution;
import kamon.metric.Timer;
import kamon.tag.TagSet;

/**
 * Kamon based implementation of {@link PreparedTimer}.
 */
final class PreparedKamonTimer implements PreparedTimer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreparedKamonTimer.class);
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);

    private final String name;
    private final Map<String, String> tags;
    private final Duration maximumDuration;
    private final Consumer<StartedTimer> additionalExpirationHandling;

    private PreparedKamonTimer(final String name) {
        this(name, new HashMap<>(), Duration.ofMinutes(5), startedTimer -> {});
    }

    private PreparedKamonTimer(
            final String name,
            final Map<String, String> tags,
            final Duration maximumDuration,
            final Consumer<StartedTimer> additionalExpirationHandling
    ) {
        this.name = name;
        this.tags = tags;
        this.maximumDuration = maximumDuration;
        this.additionalExpirationHandling = additionalExpirationHandling;
    }

    static PreparedTimer newTimer(final String name) {
        return new PreparedKamonTimer(name);
    }

    private static void defaultExpirationHandling(
            final String tracingFilter,
            final StartedTimer timer,
            @Nullable final Consumer<StartedTimer> additionalExpirationHandling
    ) {
        LOGGER.trace("Trace for {} stopped. Cause: Timer expired", tracingFilter);

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

    private static void cancelScheduledExpiration(final StoppedTimer timer, final ScheduledFuture<?> expirationFuture) {
        if (!expirationFuture.isDone()) {
            final boolean canceled = expirationFuture.cancel(false);
            if (canceled) {
                LOGGER.trace("Canceled expiration handling of MutableKamonTimer <{}> because it has been stopped " +
                        "before timeout", timer.getName());
            }
        }
    }

    @Override
    public PreparedTimer tags(final Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    @Override
    public Optional<String> getTag(final String key) {
        return Optional.ofNullable(tags.get(key));
    }

    @Override
    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }

    @Override
    public PreparedTimer tag(final String key, final String value) {
        tags.put(key, value);
        return this;
    }

    /**
     * Starts the Timer. This method is package private so only {@link Timers} can start
     * this timer.
     *
     * @return The started {@link StartedTimer}
     */
    public StartedTimer start() {
        final StartedTimer timer = StartedKamonTimer.fromPreparedTimer(this);
        final ScheduledFuture<?> expirationFuture = SCHEDULER.schedule(
                () -> defaultExpirationHandling(timer.getName(), timer, additionalExpirationHandling),
                maximumDuration.toMillis(), TimeUnit.MILLISECONDS);
        timer.onStop(stoppedTimer -> cancelScheduledExpiration(stoppedTimer, expirationFuture));
        return timer;
    }

    @Override
    public PreparedTimer record(final long time, final TimeUnit timeUnit) {
        getKamonInternalTimer().record(timeUnit.toNanos(time));
        return this;
    }

    @Override
    public Long getTotalTime() {
        return getSnapshot(false).map(Distribution::sum).orElse(0L);
    }

    @Override
    public Long getNumberOfRecords() {
        return getSnapshot(false).map(Distribution::count).orElse(0L);
    }

    @Override
    public PreparedTimer maximumDuration(final Duration maximumDuration) {
        return new PreparedKamonTimer(name, tags, maximumDuration, additionalExpirationHandling);
    }

    @Override
    public PreparedTimer onExpiration(final Consumer<StartedTimer> additionalExpirationHandling) {
        return new PreparedKamonTimer(name, tags, maximumDuration, additionalExpirationHandling);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean reset() {
        try {
            getSnapshot(true);
            LOGGER.trace("Reset timer with name <{}>", name);
        } catch (final IllegalStateException e) {
            LOGGER.warn("Could not reset Kamon timer.", e);
            return false;
        }
        return true;
    }

    private Optional<Distribution> getSnapshot(boolean reset) {
        final Timer kamonInternalTimer = getKamonInternalTimer();
        if (kamonInternalTimer instanceof Timer.Atomic atomic) {
            return Optional.of(atomic.snapshot(reset));
        } else {
            LOGGER.warn("Could not get snapshot of kamon timer");
            return Optional.empty();
        }
    }

    private kamon.metric.Timer getKamonInternalTimer() {
        return Kamon.timer(name).withTags(TagSet.from(new HashMap<>(tags)));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                "]";
    }

}
