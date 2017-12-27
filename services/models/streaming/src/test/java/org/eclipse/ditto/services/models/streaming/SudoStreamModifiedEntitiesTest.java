/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.streaming;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoStreamModifiedEntities}.
 */
public final class SudoStreamModifiedEntitiesTest {

    private static final Instant KNOWN_START = Instant.EPOCH;
    private static final Instant KNOWN_END = Instant.now();
    private static final int KNOWN_BURST = 1234;
    private static final long KNOWN_TIMEOUT = 60_000L;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoStreamModifiedEntities.TYPE)
            .set(SudoStreamModifiedEntities.JSON_START, KNOWN_START.toString())
            .set(SudoStreamModifiedEntities.JSON_END, KNOWN_END.toString())
            .set(SudoStreamModifiedEntities.JSON_BURST, KNOWN_BURST)
            .set(SudoStreamModifiedEntities.JSON_TIMEOUT_MILLIS, KNOWN_TIMEOUT)
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoStreamModifiedEntities.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoStreamModifiedEntities.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final SudoStreamModifiedEntities underTest =
                SudoStreamModifiedEntities.of(KNOWN_START, KNOWN_END, KNOWN_BURST, KNOWN_TIMEOUT, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SudoStreamModifiedEntities underTest =
                SudoStreamModifiedEntities.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        final SudoStreamModifiedEntities expectedCommand =
                SudoStreamModifiedEntities.of(KNOWN_START, KNOWN_END, KNOWN_BURST, KNOWN_TIMEOUT, EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest).isEqualTo(expectedCommand);
    }


    @Test
    public void parseWithRegistry() {
        final SudoStreamModifiedEntities expected =
                SudoStreamModifiedEntities.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        final Jsonifiable parsed =
                StreamingRegistry.newInstance().parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(expected).isEqualTo(parsed);
    }

}
