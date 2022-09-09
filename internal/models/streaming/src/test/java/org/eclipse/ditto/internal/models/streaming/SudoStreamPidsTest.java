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
package org.eclipse.ditto.internal.models.streaming;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoStreamPids}.
 */
public final class SudoStreamPidsTest {

    private static final int KNOWN_BURST = 1234;
    private static final long KNOWN_TIMEOUT = 60_000L;
    private static final EntityType THING_TYPE = EntityType.of("thing");
    private static final EntityIdWithRevision<?> KNOWN_LOWER_BOUND =
            LowerBound.fromJson(JsonFactory.newObject("{\"type\":\"thing\",\"id\":\"myId\",\"revision\":5}"));

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoStreamPids.TYPE)
            .set(SudoStreamPids.JSON_BURST, KNOWN_BURST)
            .set(SudoStreamPids.JSON_TIMEOUT_MILLIS, KNOWN_TIMEOUT)
            .set(SudoStreamPids.JSON_LOWER_BOUND, KNOWN_LOWER_BOUND.toJson())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoStreamPids.class, areImmutable(), provided(EntityIdWithRevision.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoStreamPids.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final SudoStreamPids underTest = SudoStreamPids.of(KNOWN_BURST, KNOWN_TIMEOUT, EMPTY_DITTO_HEADERS, THING_TYPE)
                .withLowerBound(KNOWN_LOWER_BOUND);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SudoStreamPids underTest =
                SudoStreamPids.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        final SudoStreamPids expectedCommand =
                SudoStreamPids.of(KNOWN_BURST, KNOWN_TIMEOUT, EMPTY_DITTO_HEADERS, THING_TYPE)
                        .withLowerBound(KNOWN_LOWER_BOUND);

        assertThat(underTest).isNotNull();
        assertThat(underTest).isEqualTo(expectedCommand);
    }

    @Test
    public void parseWithRegistry() {
        final SudoStreamPids expected =
                SudoStreamPids.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        final Jsonifiable parsed = GlobalCommandRegistry.getInstance().parse(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        assertThat(parsed).isEqualTo(expected);
    }

}
