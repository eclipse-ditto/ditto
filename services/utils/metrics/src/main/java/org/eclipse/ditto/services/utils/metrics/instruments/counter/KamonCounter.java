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
package org.eclipse.ditto.services.utils.metrics.instruments.counter;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.LongAdderCounter;
import kamon.metric.MetricValue;

/**
 * Kamon based implementation of {@link Counter}.
 */
public class KamonCounter implements Counter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonCounter.class);
    private final String name;
    private final Map<String, String> tags;

    private KamonCounter(final String name) {
        this.name = name;
        this.tags = new HashMap<>();
    }

    public static Counter newCounter(final String name) {
        return new KamonCounter(name);
    }

    @Override
    public Counter tag(final String key, final String value) {
        this.tags.put(key, value);
        return this;
    }

    @Override
    public Counter tags(final Map<String, String> tags) {
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
    public Counter increment() {
        getKamonInternalCounter().increment();
        return this;
    }

    @Override
    public Counter increment(final long times) {
        getKamonInternalCounter().increment(times);
        return this;
    }

    @Override
    public long getCount() {
        return getSnapshot(false).value();
    }

    private kamon.metric.Counter getKamonInternalCounter() {
        return Kamon.counter(name).refine(this.tags);
    }

    @Override
    public boolean reset() {
        try {
            getSnapshot(true);
            LOGGER.debug("Reset histogram with name <{}>.", name);
            return true;
        } catch (IllegalStateException e) {
            LOGGER.warn("Could not reset histogram with name <{}>.", name);
            return false;
        }
    }

    private MetricValue getSnapshot(final boolean reset) {
        final kamon.metric.Counter kamonInternalCounter = getKamonInternalCounter();
        if (kamonInternalCounter instanceof LongAdderCounter) {
            return ((LongAdderCounter) kamonInternalCounter).snapshot(reset);
        }

        throw new IllegalStateException(String.format("Could not get snapshot of Kamon counter with name <%s>", name));
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                "]";
    }
}
