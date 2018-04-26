/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ImmutableSourceTest {

    private static final String AMQP_SOURCE1 = "amqp/source1";
    private static final Source EXPECTED_SOURCE = ImmutableSource.of(2, AMQP_SOURCE1);

    private static final JsonObject KNOWN_SOURCE_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add(AMQP_SOURCE1).build())
            .set(Source.JsonFields.CONSUMER_COUNT, 2)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSource.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSource.class, areImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = EXPECTED_SOURCE.toJson();
        assertThat(actual).isEqualTo(KNOWN_SOURCE_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Source actual = ImmutableSource.fromJson(KNOWN_SOURCE_JSON);
        assertThat(actual).isEqualTo(EXPECTED_SOURCE);
    }
}
