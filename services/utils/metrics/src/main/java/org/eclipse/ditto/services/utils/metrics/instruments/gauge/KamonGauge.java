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
package org.eclipse.ditto.services.utils.metrics.instruments.gauge;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.metric.AtomicLongGauge;

/**
 * Kamon based implementation of {@link Gauge}.
 */
public class KamonGauge implements Gauge {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonGauge.class);
    private final String name;
    private final Map<String, String> tags;

    private KamonGauge(final String name) {
        this.name = name;
        this.tags = new HashMap<>();
    }

    public static Gauge newGauge(final String name) {
        return new KamonGauge(name);
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
        this.tags.put(key, value);
        return this;
    }

    @Override
    public Gauge tags(final Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        return this.tags.get(key);
    }

    @Override
    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }

    /**
     * Sets the value of the gauge to 0.
     *
     * @return True if value could be set successfully.
     */
    @Override
    public boolean reset() {
        getKamonInternalGauge().set(0);
        LOGGER.debug("Reset histogram with name <{}>.", name);
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
