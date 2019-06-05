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
package org.eclipse.ditto.services.models.streaming;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.GlobalCommandRegistry;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.models.streaming.SudoStreamSnapshotRevisions}.
 */
public final class SudoStreamSnapshotRevisionsTest {

    private static final int KNOWN_BURST = 1234;
    private static final long KNOWN_TIMEOUT = 60_000L;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoStreamSnapshotRevisions.TYPE)
            .set(SudoStreamSnapshotRevisions.JSON_BURST, KNOWN_BURST)
            .set(SudoStreamSnapshotRevisions.JSON_TIMEOUT_MILLIS, KNOWN_TIMEOUT)
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoStreamSnapshotRevisions.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoStreamSnapshotRevisions.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final SudoStreamSnapshotRevisions underTest =
                SudoStreamSnapshotRevisions.of(KNOWN_BURST, KNOWN_TIMEOUT, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SudoStreamSnapshotRevisions underTest =
                SudoStreamSnapshotRevisions.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        final SudoStreamSnapshotRevisions expectedCommand =
                SudoStreamSnapshotRevisions.of(KNOWN_BURST, KNOWN_TIMEOUT, EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest).isEqualTo(expectedCommand);
    }

    @Test
    public void parseWithRegistry() {
        final SudoStreamSnapshotRevisions expected =
                SudoStreamSnapshotRevisions.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        final Jsonifiable parsed = GlobalCommandRegistry.getInstance().parse(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        assertThat(parsed).isEqualTo(expected);
    }

}
