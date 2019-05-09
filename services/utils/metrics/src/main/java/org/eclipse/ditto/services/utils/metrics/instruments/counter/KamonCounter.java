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
package org.eclipse.ditto.services.utils.metrics.instruments.counter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.LongAdderCounter;
import kamon.metric.MetricValue;

/**
 * Kamon based implementation of {@link Counter}.
 */
@Immutable
public class KamonCounter implements Counter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonCounter.class);

    private final String name;
    private final Map<String, String> tags;

    private KamonCounter(final String name, final Map<String, String> tags) {
        this.name = name;
        this.tags = Collections.unmodifiableMap(new HashMap<>(tags));
    }

    public static Counter newCounter(final String name) {
        return new KamonCounter(name, Collections.emptyMap());
    }

    @Override
    public Counter tag(final String key, final String value) {
        final HashMap<String, String> newMap = new HashMap<>(tags);
        newMap.put(key, value);
        return new KamonCounter(name, newMap);
    }

    @Override
    public Counter tags(final Map<String, String> tags) {
        final HashMap<String, String> newMap = new HashMap<>(this.tags);
        newMap.putAll(tags);
        return new KamonCounter(name, newMap);
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        return tags.get(key);
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
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
            LOGGER.trace("Reset histogram with name <{}>.", name);
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
