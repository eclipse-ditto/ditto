/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;

import pekko.contrib.persistence.mongodb.MetricsBuilder;

/**
 * Pins the {@code metrics-builder.class} of the shipped {@code ditto-pekko-config.conf} against
 * {@link MongoMetricsBuilder}: a stale class name would only fail at runtime when the Pekko MongoDB plugin starts.
 */
public final class MongoMetricsBuilderConfigTest {

    private static final String METRICS_BUILDER_CLASS_PATH =
            "pekko.contrib.persistence.mongodb.mongo.metrics-builder.class";

    @Test
    public void shippedConfigNamesLoadableMetricsBuilder() throws ClassNotFoundException {
        final Config shippedConfig = ConfigFactory.parseResources("ditto-pekko-config.conf")
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false).setAllowUnresolved(true));

        final String metricsBuilderClass = shippedConfig.getString(METRICS_BUILDER_CLASS_PATH);

        assertThat(metricsBuilderClass).isEqualTo(MongoMetricsBuilder.class.getName());
        assertThat(MetricsBuilder.class).isAssignableFrom(Class.forName(metricsBuilderClass));
    }

}
