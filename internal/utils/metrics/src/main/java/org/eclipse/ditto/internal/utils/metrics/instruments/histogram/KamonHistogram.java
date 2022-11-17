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
package org.eclipse.ditto.internal.utils.metrics.instruments.histogram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.Distribution;
import scala.collection.Seq;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Kamon based implementation of {@link Histogram}.
 */
@Immutable
public final class KamonHistogram implements Histogram {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonHistogram.class);

    private final String name;
    private final TagSet tags;

    private KamonHistogram(final String name, final TagSet tags) {
        this.name = name;
        this.tags = tags;
    }

    public static Histogram newHistogram(final String name) {
        return new KamonHistogram(name, TagSet.empty());
    }

    @Override
    public Histogram tag(final Tag tag) {
        return new KamonHistogram(name, tags.putTag(tag));
    }

    @Override
    public Histogram tags(final TagSet tags) {
        return new KamonHistogram(name, this.tags.putAllTags(tags));
    }

    @Override
    public TagSet getTagSet() {
        return tags;
    }

    @Override
    public boolean reset() {
        try {
            getSnapshot(true);
            LOGGER.trace("Reset histogram with name <{}>.", name);
        } catch (final IllegalStateException e) {
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
        final Seq<Distribution.Bucket> buckets = getSnapshot(false).map(Distribution::buckets)
                .orElseGet(() -> CollectionConverters.asScala(Collections.<Distribution.Bucket>emptyList()).toSeq());
        buckets.foreach(bucket -> addBucketValuesToList(bucket, values));
        return values.toArray(new Long[0]);
    }

    private static List<Long> addBucketValuesToList(final Distribution.Bucket bucket, final List<Long> values) {
        for (int i = 0; i < bucket.frequency(); i++) {
            values.add(bucket.value());
        }
        return values;
    }

    private Optional<Distribution> getSnapshot(final boolean reset) {
        final kamon.metric.Histogram histogram = getKamonInternalHistogram();

        if (histogram instanceof kamon.metric.Histogram.Atomic atomic) {
            return Optional.of(atomic.snapshot(reset));
        }
        LOGGER.warn("Could not get snapshot of kamon internal histogram");
        return Optional.empty();
    }

    private kamon.metric.Histogram getKamonInternalHistogram() {
        return Kamon.histogram(name).withTags(KamonTagSetConverter.getKamonTagSet(tags));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                "]";
    }

}
