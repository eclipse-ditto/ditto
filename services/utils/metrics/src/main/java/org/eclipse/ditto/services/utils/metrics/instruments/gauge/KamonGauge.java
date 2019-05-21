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
package org.eclipse.ditto.services.utils.metrics.instruments.gauge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.AtomicLongGauge;

/**
 * Kamon based implementation of {@link Gauge}.
 */
@Immutable
public class KamonGauge implements Gauge {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonGauge.class);

    private final String name;
    private final Map<String, String> tags;

    private KamonGauge(final String name, final Map<String, String> tags) {
        this.name = name;
        this.tags = Collections.unmodifiableMap(new HashMap<>(tags));
    }

    public static Gauge newGauge(final String name) {
        return new KamonGauge(name, Collections.emptyMap());
    }

    @Override
    public Gauge increment() {
        getKamonInternalGauge().increment();
        return this;
    }

    @Override
    public Gauge decrement() {
        getKamonInternalGauge().decrement();
        return this;
    }

    @Override
    public void set(final Long value) {
        getKamonInternalGauge().set(value);
    }

    @Override
    public Long get() {
        final kamon.metric.Gauge kamonInternalGauge = getKamonInternalGauge();
        if (kamonInternalGauge instanceof AtomicLongGauge) {
            return ((AtomicLongGauge) kamonInternalGauge).snapshot().value();
        }
        throw new IllegalStateException("Could not get value from kamon gauge");
    }

    @Override
    public Gauge tag(final String key, final String value) {
        final HashMap<String, String> newMap = new HashMap<>(tags);
        newMap.put(key, value);
        return new KamonGauge(name, newMap);
    }

    @Override
    public Gauge tags(final Map<String, String> tags) {
        final HashMap<String, String> newMap = new HashMap<>(this.tags);
        newMap.putAll(tags);
        return new KamonGauge(name, newMap);
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        return this.tags.get(key);
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * Sets the value of the gauge to 0.
     *
     * @return True if value could be set successfully.
     */
    @Override
    public boolean reset() {
        getKamonInternalGauge().set(0);
        LOGGER.trace("Reset histogram with name <{}>.", name);
        return true;
    }

    private kamon.metric.Gauge getKamonInternalGauge() {
        return Kamon.gauge(name).refine(tags);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                "]";
    }
}
