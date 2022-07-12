/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SudoUpdateThingResponse}.
 */
public final class SudoUpdateThingResponseTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SudoUpdateThingResponse.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(ThingId.class, PolicyId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoUpdateThingResponse.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SudoUpdateThingResponse command =
                SudoUpdateThingResponse.of(ThingId.of("namespace", "name"), 7L, PolicyId.of("policy:id"), 9L, true,
                        dittoHeaders);
        final String jsonString = command.toJsonString();
        final SudoUpdateThingResponse deserializedCommand =
                SudoUpdateThingResponse.fromJson(JsonObject.of(jsonString), dittoHeaders);
        assertThat(deserializedCommand).isEqualTo(command);
    }

    @Test
    public void testSerializationWithNullPolicyId() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SudoUpdateThingResponse command =
                SudoUpdateThingResponse.of(ThingId.of("namespace", "name"), 7L, null, 9L, true, dittoHeaders);
        final String jsonString = command.toJsonString();
        final SudoUpdateThingResponse deserializedCommand =
                SudoUpdateThingResponse.fromJson(JsonObject.of(jsonString), dittoHeaders);
        assertThat(deserializedCommand).isEqualTo(command);
    }

}
