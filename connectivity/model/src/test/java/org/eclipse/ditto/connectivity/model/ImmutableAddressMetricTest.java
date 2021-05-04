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

package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAddressMetric}.
 */
public class ImmutableAddressMetricTest {

    private static final JsonObject METRIC_JSON =
            JsonObject.newBuilder()
                    .set("consumed",
                            JsonObject.newBuilder()
                                    .set("success", TestConstants.MEASUREMENTS)
                                    .set("failure", TestConstants.MEASUREMENTS)
                                    .build()
                    )
                    .set("mapped",
                            JsonObject.newBuilder()
                                    .set("success", TestConstants.MEASUREMENTS)
                                    .set("failure", TestConstants.MEASUREMENTS)
                                    .build()
                    )
                    .build();

    private static final AddressMetric ADDRESS_METRIC = ConnectivityModelFactory.newAddressMetric(
            TestConstants.INBOUND_MEASUREMENTS);

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAddressMetric.class)
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAddressMetric.class, areImmutable(),
                provided(Measurement.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = ADDRESS_METRIC.toJson();
        assertThat(actual).isEqualTo(METRIC_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final AddressMetric actual = ImmutableAddressMetric.fromJson(METRIC_JSON);
        assertThat(actual).isEqualTo(ADDRESS_METRIC);
    }

}
