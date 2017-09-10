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
package org.eclipse.ditto.model.base.json;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;
import java.util.function.Predicate;

import org.junit.Test;

/**
 * Unit test for {@link JsonSchemaVersion}.
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
        final int known = 1;
        final Optional<JsonSchemaVersion> jsonSchemaVersion = JsonSchemaVersion.forInt(known);

        assertThat(jsonSchemaVersion).contains(JsonSchemaVersion.V_1);
    }

}
