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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.AtomicHdrHistogram;
import kamon.metric.Bucket;
import kamon.metric.Histogram;
import kamon.metric.MetricDistribution;
import kamon.metric.Timer;
import kamon.metric.TimerImpl;
import scala.collection.Seq;

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
    public Long[] getRecords() {
        final List<Long> values = new ArrayList<>();
        final Seq<Bucket> buckets = getSnapshot(false).distribution().buckets();
        buckets.toStream().foreach(bucket -> addBucketValuesToList(bucket, values));
        return values.toArray(new Long[0]);
    }

    @Override
    public Long getNumberOfRecords() {
        return getSnapshot(false).distribution().count();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean reset() {
        try {
            getSnapshot(true);
            LOGGER.debug("Reset timer with name <{}>", name);
        } catch (IllegalStateException e) {
            LOGGER.warn("Could not reset Kamon timer.", e);
            return false;
        }
        return true;
    }

    private MetricDistribution getSnapshot(boolean reset) {
        final Timer kamonInternalTimer = getKamonInternalTimer();
        Histogram histogram;
        if (kamonInternalTimer instanceof TimerImpl) {
            histogram = ((TimerImpl) kamonInternalTimer).histogram();
        } else {
            throw new IllegalStateException("Could not get snapshot of kamon timer");
        }

        if (histogram instanceof AtomicHdrHistogram) {
            return ((AtomicHdrHistogram) histogram).snapshot(reset);
        }
        throw new IllegalStateException("Could not get snapshot of kamon timer");
    }

    private kamon.metric.Timer getKamonInternalTimer() {
        return Kamon.timer(name).refine(this.tags);
    }

    private List<Long> addBucketValuesToList(Bucket bucket, List<Long> values) {
        for (int i = 0; i < bucket.frequency(); i++) {
            values.add(bucket.value());
        }
        return values;
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                "]";
    }
}
