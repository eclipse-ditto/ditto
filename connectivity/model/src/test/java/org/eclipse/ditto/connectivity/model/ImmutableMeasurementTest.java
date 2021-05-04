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

public class ImmutableMeasurementTest {

    private static final MetricType TYPE = MetricType.CONSUMED;
    private static final boolean SUCCESS = true;
    private static final Measurement MEASUREMENT = new ImmutableMeasurement(TYPE, SUCCESS, TestConstants.COUNTERS, TestConstants.INSTANT);
    private static final JsonObject MEASUREMENT_JSON = TestConstants.getMeasurementJson(TYPE, SUCCESS);

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableMeasurement.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableMeasurement.class, areImmutable(), provided(MetricType.class).areAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = MEASUREMENT.toJson();
        assertThat(actual).isEqualTo(MEASUREMENT_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Measurement actual = ImmutableMeasurement.fromJson(MEASUREMENT_JSON);
        assertThat(actual).isEqualTo(MEASUREMENT);
    }
}
