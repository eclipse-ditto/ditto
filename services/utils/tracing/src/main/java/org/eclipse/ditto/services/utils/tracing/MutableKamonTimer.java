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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;

/**
 * A mutable timer that allows adding tags to the timer while it is running.
 */
@NotThreadSafe
@AllParametersAndReturnValuesAreNonnullByDefault
public final class MutableKamonTimer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MutableKamonTimer.class);
    private final String name;
    private long startTimestamp;
    private long endTimestamp;
    private boolean started;
    private boolean stopped;
    private Map<String, String> tags;
    private List<Consumer<MutableKamonTimer>> onStopHandlers;

    MutableKamonTimer(final String name) {
        this.name = name;
        this.tags = new ConcurrentHashMap<>();
        this.onStopHandlers = new ArrayList<>();
        this.started = false;
        this.stopped = false;
    }

    public MutableKamonTimer tags(final Map<String, String> tags) {
        if (!stopped) {
            this.tags.putAll(tags);
        } else {
            LOGGER.warn("Tried to append multiple tags to the stopped timer with name <{}>. Tags are ineffective.",
                    name);
        }
        return this;
    }

    public MutableKamonTimer tag(final String key, final long value) {
        return tag(key, Long.toString(value));
    }

    public MutableKamonTimer tag(final String key, final double value) {
        return tag(key, Double.toString(value));
    }

    public MutableKamonTimer tag(final String key, final boolean value) {
        return tag(key, Boolean.toString(value));
    }

    public MutableKamonTimer tag(final String key, final String value) {
        if (!stopped) {
            this.tags.put(key, value);
        } else {
            LOGGER.warn(
                    "Tried to append tag <{}> with value <{}> to the stopped timer with name <{}>. Tag is ineffective.",
                    key, value, name);
        }
        return this;
    }

    /**
     * Starts the MutableKamonTimer. This method is package private so only {@link MutableKamonTimerBuilder} can start
     * this timer.
     *
     * @return The started {@link MutableKamonTimer}
     */
    MutableKamonTimer start() {
        if (!started) {
            this.started = true;
            this.startTimestamp = System.nanoTime();
            LOGGER.debug("MutableKamonTimer with name <{]> was started", name);
        } else {
            LOGGER.warn("Tried to start the already running MutableKamonTimer with name <{}>", name);
        }

        return this;
    }

    public synchronized MutableKamonTimer stop() {
        if (started && !stopped) {
            this.stopped = true;
            this.endTimestamp = System.nanoTime();
            final long duration = endTimestamp - this.startTimestamp;
            Kamon.timer(name).refine(this.tags).record(duration);
            LOGGER.debug("MutableKamonTimer with name <{}> was stopped after <{}> nanoseconds", name, duration);
            onStopHandlers.forEach(onStopHandler -> onStopHandler.accept(this));
        } else {
            LOGGER.warn("Tried to stop the not yet started MutableKamonTimer with name <{}>", name);
        }

        return this;
    }

    void onStop(Consumer<MutableKamonTimer> onStopHandler) {
        onStopHandlers.add(onStopHandler);
    }

    /**
     * Gets the duration from {@link MutableKamonTimer#startTimestamp} to {@link MutableKamonTimer#endTimestamp}
     *
     * @return The duration
     * @throws java.lang.IllegalStateException if timer has not been started and stopped before calling this method
     */
    public Duration getDuration() {
        verifyStarted();
        verifyStopped();
        return Duration.ofNanos(this.endTimestamp - this.startTimestamp);
    }

    private void verifyStarted() {
        if (!started) {
            throw new IllegalStateException("Timer has not been started, yet. Start timestamp can not be determined " +
                    "before starting the timer.");
        }
    }

    private void verifyStopped() {
        if (!stopped) {
            throw new IllegalStateException("Timer has not been stopped, yet. End timestamp can not be determined " +
                    "before stopping the timer.");
        }
    }

    @Nullable
    public String getTag(final String key) {
        return this.tags.get(key);
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return "MutableKamonTimer{" +
                "name='" + name + '\'' +
                ", startTimestamp=" + startTimestamp +
                ", endTimestamp=" + endTimestamp +
                ", running=" + started +
                ", tags=" + tags +
                '}';
    }
}
