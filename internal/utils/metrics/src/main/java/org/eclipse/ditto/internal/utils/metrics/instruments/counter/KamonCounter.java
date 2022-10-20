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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;

/**
 * Kamon based implementation of {@link Counter}.
 */
@Immutable
public final class KamonCounter implements Counter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonCounter.class);

    private final String name;
    private final TagSet tags;

    private KamonCounter(final String name, final TagSet tags) {
        this.name = argumentNotEmpty(name, "name");
        this.tags = checkNotNull(tags, "tags");
    }

    public static KamonCounter newCounter(final String name) {
        return newCounter(name, TagSet.empty());
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
    public static KamonCounter newCounter(final String name, final TagSet tags) {
        return new KamonCounter(name, tags);
    }

    @Override
    public KamonCounter tag(final Tag tag) {
        return new KamonCounter(name, tags.putTag(tag));
    }

    @Override
    public KamonCounter tags(final TagSet tags) {
        return new KamonCounter(name, this.tags.putAllTags(tags));
    }

    @Override
    public TagSet getTagSet() {
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
        return Kamon.counter(name).withTags(KamonTagSetConverter.getKamonTagSet(tags));
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
