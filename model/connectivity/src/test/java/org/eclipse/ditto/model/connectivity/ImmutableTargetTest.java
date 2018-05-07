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

public class ImmutableTargetTest {

    private static final String ADDRESS = "amqp/target1";
    private static final String THINGS_TWIN_EVENTS = "_/_/things/twin/events";
    private static final Target EXPECTED_TARGET = ImmutableTarget.of(ADDRESS, THINGS_TWIN_EVENTS);
    private static final JsonObject KNOWN_TARGET_JSON = JsonObject
            .newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder().add(THINGS_TWIN_EVENTS).build())
            .set(Target.JsonFields.ADDRESS, ADDRESS)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableTarget.class, areImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = EXPECTED_TARGET.toJson();
        assertThat(actual).isEqualTo(KNOWN_TARGET_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Target actual = ImmutableTarget.fromJson(KNOWN_TARGET_JSON);
        assertThat(actual).isEqualTo(EXPECTED_TARGET);
    }
}
