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
package org.eclipse.ditto.internal.utils.health;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link StatusDetailMessage}.
 */
public final class StatusDetailMessageTest {

    private static final StatusDetailMessage.Level KNOWN_LEVEL = StatusDetailMessage.Level.ERROR;
    private static final String KNOWN_MESSAGE_STR = "errorMsg";

    private static StatusDetailMessage KNOWN_MESSAGE =
            StatusDetailMessage.of(KNOWN_LEVEL, KNOWN_MESSAGE_STR);

    private static JsonObject KNOWN_MESSAGE_JSON = JsonFactory.newObjectBuilder()
            .set(KNOWN_LEVEL.toString(), KNOWN_MESSAGE_STR)
            .build();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(StatusDetailMessage.class, areImmutable(),
                provided(JsonValue.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StatusDetailMessage.class).verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actualJson = KNOWN_MESSAGE.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_MESSAGE_JSON);
    }

    @Test
    public void fromValidJsonObject() {
        final StatusDetailMessage actual = StatusDetailMessage.fromJson(KNOWN_MESSAGE_JSON);

        assertThat(actual).isEqualTo(KNOWN_MESSAGE);
    }

}
