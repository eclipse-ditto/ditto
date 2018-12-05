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

package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.connectivity.TestConstants.INBOUND_MEASUREMENTS;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import com.sun.jndi.cosnaming.IiopUrl;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAddressMetric}.
 */
public class ImmutableAddressMetricTest {

    private static final JsonObject METRIC_JSON =
            JsonObject.newBuilder()
                    .set("inbound",
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

    private static final AddressMetric ADDRESS_METRIC = ConnectivityModelFactory.newAddressMetric(INBOUND_MEASUREMENTS);

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
