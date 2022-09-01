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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableResourceStatus}.
 */
public class ImmutableResourceStatusTest {

    private static final Instant INSTANT = Instant.now();
    private static final ResourceStatus RESOURCE_STATUS = ImmutableResourceStatus.of(
            ResourceStatus.ResourceType.CLIENT, "client1", ConnectivityStatus.OPEN, null, "client " +
                    "connected", INSTANT);

    private static final JsonObject RESOURCE_STATUS_JSON =
            JsonObject
                    .newBuilder()
                    .set(ResourceStatus.JsonFields.TYPE, ResourceStatus.ResourceType.CLIENT.getName())
                    .set(ResourceStatus.JsonFields.CLIENT, "client1")
                    .set(ResourceStatus.JsonFields.STATUS, ConnectivityStatus.OPEN.getName())
                    .set(ResourceStatus.JsonFields.STATUS_DETAILS, "client connected")
                    .set(ResourceStatus.JsonFields.IN_STATE_SINCE, INSTANT.toString())
                    .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableResourceStatus.class).verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableResourceStatus.class, areImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = RESOURCE_STATUS.toJson();
        assertThat(actual).isEqualTo(RESOURCE_STATUS_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ResourceStatus actual = ImmutableResourceStatus.fromJson(RESOURCE_STATUS_JSON);
        assertThat(actual).isEqualTo(RESOURCE_STATUS);
    }
}
