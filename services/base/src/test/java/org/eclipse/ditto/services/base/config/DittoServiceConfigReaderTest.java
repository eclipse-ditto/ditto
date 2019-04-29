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
package org.eclipse.ditto.services.base.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DittoServiceConfigReader}.
 */

public final class DittoServiceConfigReaderTest {

    private static final String SERVICE_NAME = "dummy-service";

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoServiceConfigReader.class, areImmutable());
    }

    @Test
    public void allValuesSet() {
        final ServiceConfigReader underTest = loadResource("allValuesSet.conf");
        assertThat(underTest.cluster().numberOfShards()).isEqualTo(1234);
        assertThat(underTest.metrics().isPrometheusEnabled()).isTrue();
        assertThat(underTest.metrics().isSystemMetricsEnabled()).isTrue();
    }

    @Test
    public void noValuesSet() {
        final ServiceConfigReader underTest = DittoServiceConfigReader.from(SERVICE_NAME).apply(ConfigFactory.empty());

        assertThat(underTest.cluster().numberOfShards())
                .isEqualTo(ClusterConfigReader.DEFAULT_NUMBER_OF_SHARDS);
        assertThat(underTest.metrics().isPrometheusEnabled()).isFalse();
        assertThat(underTest.metrics().isSystemMetricsEnabled()).isFalse();
    }

    @Test
    public void someValuesSet() {
        final ServiceConfigReader underTest = loadResource("someValuesSet.conf");
        assertThat(underTest.cluster().numberOfShards()).isEqualTo(1234);
        assertThat(underTest.metrics().isPrometheusEnabled()).isTrue();
        assertThat(underTest.metrics().isSystemMetricsEnabled()).isFalse();
    }

    private static ServiceConfigReader loadResource(final String resourceName) {
        final Config config = ConfigFactory.parseResources("DittoServiceConfigReaderTest/" + resourceName);
        return DittoServiceConfigReader.from(SERVICE_NAME).apply(config);
    }
}
