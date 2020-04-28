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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThing}.
 */
public final class UpdateThingTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(UpdateThing.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(UpdateThing.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final UpdateThing command = UpdateThing.of(ThingId.of("namespace", "name"), dittoHeaders);
        final String jsonString = command.toJsonString();
        final UpdateThing deserializedCommand = UpdateThing.fromJson(JsonObject.of(jsonString), dittoHeaders);
        assertThat(deserializedCommand).isEqualTo(command);
    }

}
