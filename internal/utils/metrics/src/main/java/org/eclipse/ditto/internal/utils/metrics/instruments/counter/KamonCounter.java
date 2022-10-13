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
package org.eclipse.ditto.internal.utils.metrics.instruments.counter;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.tag.TagSet;

/**
 * Kamon based implementation of {@link Counter}.
 */
@Immutable
public final class KamonCounter implements Counter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonCounter.class);

    private final String name;
    private final Map<String, String> tags;

    private KamonCounter(final String name, final Map<String, String> tags) {
        this.name = argumentNotEmpty(name, "name");
        this.tags = Collections.unmodifiableMap(new HashMap<>(checkNotNull(tags, "tags")));
    }

    public static KamonCounter newCounter(final String name) {
        return newCounter(name, Collections.emptyMap());
    }

    /**
     * Returns a new instance of {@code KamonCounter}
     *
     * @param name the name of the returned counter.
     * @param tags tags of the returned counter.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public static KamonCounter newCounter(final String name, final Map<String, String> tags) {
        return new KamonCounter(name, tags);
    }

    @Override
    public KamonCounter tag(final String key, final String value) {
        final HashMap<String, String> newMap = new HashMap<>(tags);
        newMap.put(key, value);
        return new KamonCounter(name, newMap);
    }

    @Override
    public KamonCounter tags(final Map<String, String> tags) {
        final HashMap<String, String> newMap = new HashMap<>(this.tags);
        newMap.putAll(tags);
        return new KamonCounter(name, newMap);
    }

    @Override
    public Optional<String> getTag(final String key) {
        return Optional.ofNullable(tags.get(key));
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public KamonCounter increment() {
        getKamonInternalCounter().increment();
        return this;
    }

    @Override
    public KamonCounter increment(final long times) {
        getKamonInternalCounter().increment(times);
        return this;
    }

    @Override
    public long getCount() {
        return getSnapshot();
    }

    private long getSnapshot() {
        final kamon.metric.Counter kamonInternalCounter = getKamonInternalCounter();
        if (kamonInternalCounter instanceof kamon.metric.Counter.LongAdder longAdder) {
            return longAdder.snapshot(false);
        }
        LOGGER.warn("Could not get snapshot of Kamon counter with name <{}>!", name);
        return 0L;
    }

    private kamon.metric.Counter getKamonInternalCounter() {
        return Kamon.counter(name).withTags(TagSet.from(new HashMap<>(tags)));
    }

    @Override
    public boolean reset() {
        try {
            final kamon.metric.Counter kamonInternalCounter = getKamonInternalCounter();
            if (kamonInternalCounter instanceof kamon.metric.Counter.LongAdder longAdder) {
                longAdder.snapshot(true);
                LOGGER.trace("Reset counter with name <{}>.", name);
                return true;
            }
            LOGGER.warn("Could not reset counter with name <{}>.", name);
            return false;
        } catch (final IllegalStateException e) {
            LOGGER.warn("Could not reset counter with name <{}>.", name);
            return false;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                "]";
    }

}
