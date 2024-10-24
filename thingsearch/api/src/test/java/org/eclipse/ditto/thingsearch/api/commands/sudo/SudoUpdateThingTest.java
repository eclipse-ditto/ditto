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
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SudoUpdateThing}.
 */
public final class SudoUpdateThingTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoUpdateThing.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SudoUpdateThing command =
                SudoUpdateThing.of(ThingId.of("namespace", "name"), true, false, UpdateReason.UNKNOWN, dittoHeaders);
        final String jsonString = command.toJsonString();
        final SudoUpdateThing deserializedCommand = SudoUpdateThing.fromJson(JsonObject.of(jsonString), dittoHeaders);

        assertThat(deserializedCommand).isEqualTo(command);
    }

}
