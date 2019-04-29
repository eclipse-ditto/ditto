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
package org.eclipse.ditto.services.utils.metrics.mongo;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;

import akka.contrib.persistence.mongodb.MetricsBuilder;
import akka.contrib.persistence.mongodb.MongoHistogram;
import akka.contrib.persistence.mongodb.MongoTimer;

/**
 * An akka-persistence-mongodb {@link MetricsBuilder} which uses {@link DittoMetrics} in order to provide timers and
 * histograms.
 */
@Immutable
public final class MongoMetricsBuilder implements MetricsBuilder {

    @Override
    public MongoTimer timer(final String name) {
        return () -> {
            final StartedTimer startedTimer = DittoMetrics.timer(name).start();

            return () -> startedTimer.stop().getDuration().toNanos();
        };
    }

    @Override
    public MongoHistogram histogram(final String name) {
        return value -> DittoMetrics.histogram(name).record((long) value);
    }
}
