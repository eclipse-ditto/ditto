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
 * Pins the {@code metrics-builder.class} of the shipped MongoDB persistence profile
 * {@code ditto-mongo-persistence.conf} against {@link MongoMetricsBuilder}: a stale class name would only fail at
 * runtime when the Pekko MongoDB plugin starts. Also pins that the {@code pekko.contrib.persistence.mongodb} block
 * moved out of the backend-neutral {@code ditto-pekko-config.conf} rather than being copied.
 */
public final class MongoMetricsBuilderConfigTest {

    private static final String METRICS_BUILDER_CLASS_PATH =
            "pekko.contrib.persistence.mongodb.mongo.metrics-builder.class";
    private static final String MONGO_PLUGIN_PATH = "pekko.contrib.persistence.mongodb";

    @Test
    public void shippedConfigNamesLoadableMetricsBuilder() throws ClassNotFoundException {
        final Config shippedConfig = ConfigFactory.parseResources("ditto-mongo-persistence.conf")
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false).setAllowUnresolved(true));

        final String metricsBuilderClass = shippedConfig.getString(METRICS_BUILDER_CLASS_PATH);

        assertThat(metricsBuilderClass).isEqualTo(MongoMetricsBuilder.class.getName());
        assertThat(MetricsBuilder.class).isAssignableFrom(Class.forName(metricsBuilderClass));
    }

    @Test
    public void backendNeutralPekkoConfigHasNoMongoPluginBlock() {
        final Config pekkoConfig = ConfigFactory.parseResources("ditto-pekko-config.conf");

        assertThat(pekkoConfig.isEmpty()).as("ditto-pekko-config.conf must be on the classpath").isFalse();
        assertThat(pekkoConfig.hasPath(MONGO_PLUGIN_PATH)).isFalse();
    }

}
