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
package org.eclipse.ditto.services.base.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.InetSocketAddress;
import java.time.Duration;

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
        assertThat(underTest.getClusterConfigReader().getNumberOfShards()).isEqualTo(1234);
        assertThat(underTest.getClusterConfigReader().isMajorityCheckEnabled()).isTrue();
        assertThat(underTest.getClusterConfigReader().getMajorityCheckDelay()).isEqualTo(Duration.ofHours(500));
        assertThat(underTest.getStatsdConfigReader().getStatsd())
                .contains(InetSocketAddress.createUnresolved("statsdhost", 5678));
    }

    @Test
    public void noValuesSet() {
        final ServiceConfigReader underTest = DittoServiceConfigReader.from(SERVICE_NAME).apply(ConfigFactory.empty());

        assertThat(underTest.getClusterConfigReader().getNumberOfShards())
                .isEqualTo(ClusterConfigReader.DEFAULT_NUMBER_OF_SHARDS);
        assertThat(underTest.getClusterConfigReader().isMajorityCheckEnabled())
                .isEqualTo(ClusterConfigReader.DEFAULT_MAJORITY_CHECK_ENABLED);
        assertThat(underTest.getClusterConfigReader().getMajorityCheckDelay())
                .isEqualTo(ClusterConfigReader.DEFAULT_MAJORITY_CHECK_DELAY);
        assertThat(underTest.getStatsdConfigReader().getStatsd()).isEmpty();
    }

    @Test
    public void someValuesSet() {
        final ServiceConfigReader underTest = loadResource("someValuesSet.conf");
        assertThat(underTest.getClusterConfigReader().getNumberOfShards()).isEqualTo(1234);
        assertThat(underTest.getClusterConfigReader().isMajorityCheckEnabled()).isTrue();
        assertThat(underTest.getClusterConfigReader().getMajorityCheckDelay())
                .isEqualTo(ClusterConfigReader.DEFAULT_MAJORITY_CHECK_DELAY);
        assertThat(underTest.getStatsdConfigReader().getStatsd()).isEmpty();
    }

    private static ServiceConfigReader loadResource(final String resourceName) {
        final Config config = ConfigFactory.parseResources("DittoServiceConfigReaderTest/" + resourceName);
        return DittoServiceConfigReader.from(SERVICE_NAME).apply(config);
    }
}