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
import java.util.concurrent.ConcurrentHashMap;

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
    private boolean running;
    private Map<String, String> tags;

    MutableKamonTimer(final String name) {
        this.name = name;
        this.tags = new ConcurrentHashMap<>();
        this.running = false;
    }

    public MutableKamonTimer tags(final Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    public MutableKamonTimer tag(final String key, final String value) {
        this.tags.put(key, value);
        return this;
    }

    public MutableKamonTimer tag(final String key, final long value) {
        this.tags.put(key, Long.toString(value));
        return this;
    }

    public MutableKamonTimer tag(final String key, final double value) {
        this.tags.put(key, Double.toString(value));
        return this;
    }

    public MutableKamonTimer tag(final String key, final boolean value) {
        this.tags.put(key, Boolean.toString(value));
        return this;
    }

    /**
     * Starts the MutableKamonTimer. This method is package private so only {@link MutableKamonTimerBuilder} can start
     * this timer.
     * @return The started {@link MutableKamonTimer}
     */
    MutableKamonTimer start() {
        if (!running) {
            this.running = true;
            this.startTimestamp = System.nanoTime();
            LOGGER.debug("MutableKamonTimer with name <{]> was started", name);
        }else{
            LOGGER.warn("Tried to start the already running MutableKamonTimer with name <{}>");
        }

        return this;
    }

    public synchronized MutableKamonTimer stop() {
        if (running) {
            this.running = false;
            this.endTimestamp = System.nanoTime();
            Kamon.timer(name).refine(this.tags).record(endTimestamp - this.startTimestamp);
            LOGGER.debug("MutableKamonTimer with name <{]> was stopped", name);
        }else {
            LOGGER.warn("Tried to stop the not running MutableKamonTimer with name <{}>");
        }

        return this;
    }

    public long getStartTimestamp() {
        return this.startTimestamp;
    }

    public long getEndTimestamp() {
        return this.endTimestamp;
    }

    @Nullable
    public String getTag(final String key) {
        return this.tags.get(key);
    }

    @Override
    public String toString() {
        return "MutableKamonTimer{" +
                "name='" + name + '\'' +
                ", startTimestamp=" + startTimestamp +
                ", endTimestamp=" + endTimestamp +
                ", running=" + running +
                ", tags=" + tags +
                '}';
    }
}
