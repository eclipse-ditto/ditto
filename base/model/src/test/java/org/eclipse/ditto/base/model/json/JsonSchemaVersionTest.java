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
package org.eclipse.ditto.base.model.json;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;
import java.util.function.Predicate;

import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.json.JsonSchemaVersion}.
 */
public final class JsonSchemaVersionTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonSchemaVersion.class, areImmutable(), provided(Predicate.class).isAlsoImmutable());
    }


    @Test
    public void getSchemaVersionForUnknownVersionIntReturnsEmptyOptional() {
        final int unknown = -1;
        final Optional<JsonSchemaVersion> jsonSchemaVersion = JsonSchemaVersion.forInt(unknown);

        assertThat(jsonSchemaVersion).isEmpty();
    }


    @Test
    public void getSchemaVersionForKnownVersionIntReturnsExpected() {
        final int known = 2;
        final Optional<JsonSchemaVersion> jsonSchemaVersion = JsonSchemaVersion.forInt(known);

        assertThat(jsonSchemaVersion).contains(JsonSchemaVersion.V_2);
    }

}
