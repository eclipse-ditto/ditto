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
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
public class PreparedKamonTimer implements PreparedTimer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreparedKamonTimer.class);

    private final String name;
    private final Map<String, String> tags;

    private PreparedKamonTimer(final String name) {
        this.name = name;
        this.tags = new HashMap<>();
    }

    static PreparedTimer newTimer(final String name) {
        return new PreparedKamonTimer(name);
    }

    @Override
    public PreparedTimer tags(final Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        return tags.get(key);
    }

    @Override
    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }

    @Override
    public PreparedTimer tag(final String key, final String value) {
        this.tags.put(key, value);
        return this;
    }

    /**
     * Starts the Timer. This method is package private so only {@link DefaultTimerBuilder} can start
     * this timer.
     *
     * @return The started {@link StartedTimer}
     */
    public StartedTimer start() {
        return StartedKamonTimer.fromPreparedTimer(this);
    }

    @Override
    public PreparedTimer record(final long time, final TimeUnit timeUnit) {
        getKamonInternalTimer().record(timeUnit.toNanos(time));
        return this;
    }

    @Override
    public Long getTotalTime() {
        return getSnapshot(false).sum();
    }

    @Override
    public Long getNumberOfRecords() {
        return getSnapshot(false).count();
    }

    @Override
    public String getName() {
        return this.name;
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

    private Distribution getSnapshot(boolean reset) {
        final Timer kamonInternalTimer = getKamonInternalTimer();
        if (kamonInternalTimer instanceof Timer.Atomic) {
            return ((Timer.Atomic) kamonInternalTimer).snapshot(reset);
        } else {
            throw new IllegalStateException("Could not get snapshot of kamon timer");
        }
    }

    private kamon.metric.Timer getKamonInternalTimer() {
        return Kamon.timer(name).withTags(TagSet.from(new HashMap<>(this.tags)));
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                "]";
    }
}
