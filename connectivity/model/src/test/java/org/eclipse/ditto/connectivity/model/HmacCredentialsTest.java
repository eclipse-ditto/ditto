/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link HmacCredentials}.
 */
public final class HmacCredentialsTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(HmacCredentials.class).verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(HmacCredentials.class, areImmutable(), provided(JsonObject.class).isAlsoImmutable());
    }

    @Test
    public void testJsonSerialization() {
        final Credentials original = HmacCredentials.of("algorithm-name", JsonObject.newBuilder()
                .set("parameterName", "parameterValue")
                .build());
        final Credentials deserialized = Credentials.fromJson(original.toJson());
        assertThat(deserialized).isEqualTo(original);
    }
}
