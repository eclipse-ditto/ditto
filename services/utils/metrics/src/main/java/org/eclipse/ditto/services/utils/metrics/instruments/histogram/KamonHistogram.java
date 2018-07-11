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
package org.eclipse.ditto.services.utils.metrics.instruments.histogram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.AtomicHdrHistogram;
import kamon.metric.Bucket;
import kamon.metric.MetricDistribution;
import scala.collection.Seq;

/**
 * Kamon based implementation of {@link Histogram}.
 */
public class KamonHistogram implements Histogram {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonHistogram.class);

    private final Map<String, String> tags;
    private final String name;

    private KamonHistogram(final String name) {
        this.name = name;
        this.tags = new HashMap<>();
    }

    public static Histogram newHistogram(final String name) {
        return new KamonHistogram(name);
    }

    @Override
    public Histogram tag(final String key, final String value) {
        this.tags.put(key, value);
        return this;
    }

    @Override
    public Histogram tags(final Map<String, String> tags) {
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
    public boolean reset() {

        try {
            getSnapshot(true);
            LOGGER.debug("Reset histogram with name <{}>.", name);
        } catch (IllegalStateException e) {
            LOGGER.warn("Could not reset Kamon timer.", e);
            return false;
        }
        return true;
    }

    @Override
    public Histogram record(final Long value) {
        getKamonInternalHistogram().record(value);
        return this;
    }

    @Override
    public Long[] getRecordedValues() {
        final List<Long> values = new ArrayList<>();
        final Seq<Bucket> buckets = getSnapshot(false).distribution().buckets();
        buckets.foreach(bucket -> addBucketValuesToList(bucket, values));
        return values.toArray(new Long[0]);
    }

    private List<Long> addBucketValuesToList(Bucket bucket, List<Long> values) {
        for (int i = 0; i < bucket.frequency(); i++) {
            values.add(bucket.value());
        }
        return values;
    }

    private MetricDistribution getSnapshot(final boolean reset) {
        final kamon.metric.Histogram histogram = getKamonInternalHistogram();

        if (histogram instanceof AtomicHdrHistogram) {
            return ((AtomicHdrHistogram) histogram).snapshot(reset);
        }

        throw new IllegalStateException("Could not get snapshot of kamon internal histogram");
    }

    private kamon.metric.Histogram getKamonInternalHistogram() {
        return Kamon.histogram(name).refine(tags);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "tags=" + tags +
                ", name=" + name +
                "]";
    }
}
