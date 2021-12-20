/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;
import org.komamitsu.fluency.Fluency;
import org.komamitsu.fluency.fluentd.FluencyBuilderForFluentd;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultConnectionMonitorRegistry}.
 */
public class DefaultConnectionMonitorRegistryTest {

    @Test
    public void fromConfig() {
        assertThat(DefaultConnectionMonitorRegistry.fromConfig(TestConstants.CONNECTIVITY_CONFIG))
                .isNotNull();
    }

    @Test
    public void testEqualsAndHashcode() {
        final Fluency red = new FluencyBuilderForFluentd().build();
        final Fluency black = new FluencyBuilderForFluentd().build("localhost", 9999);

        EqualsVerifier.forClass(DefaultConnectionMonitorRegistry.class).withPrefabValues(Fluency.class, red, black)
                .verify();
    }

}
