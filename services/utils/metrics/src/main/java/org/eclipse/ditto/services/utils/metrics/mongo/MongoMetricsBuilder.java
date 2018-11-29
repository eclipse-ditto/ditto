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
package org.eclipse.ditto.services.utils.metrics.mongo;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;

import akka.contrib.persistence.mongodb.MetricsBuilder;
import akka.contrib.persistence.mongodb.MongoHistogram;
import akka.contrib.persistence.mongodb.MongoTimer;

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
