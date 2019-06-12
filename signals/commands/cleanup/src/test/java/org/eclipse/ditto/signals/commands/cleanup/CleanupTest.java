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
package org.eclipse.ditto.signals.commands.cleanup;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Cleanup} command.
 */
public class CleanupTest {

    private static final String ID = "thing:eclipse:ditto";
    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, Cleanup.TYPE)
            .set(CleanupCommand.JsonFields.ENTITY_ID, ID)
            .build();
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("123").build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(Cleanup.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Cleanup.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = Cleanup.of(ID, DittoHeaders.empty()).toJson();
        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Cleanup commandFromJson = Cleanup.fromJson(KNOWN_JSON, HEADERS);
        final Cleanup expectedCommand = Cleanup.of(ID, HEADERS);
        assertThat(commandFromJson).isEqualTo(expectedCommand);
    }
}