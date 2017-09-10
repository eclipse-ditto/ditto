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
package org.eclipse.ditto.json;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.junit.Test;

/**
 * Unit test for {@link JsonParseException}.
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public final class JsonParseExceptionTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonParseException.class, areImmutable());
    }


    @Test
    public void getErrorCodeReturnsExpected() {
        final JsonParseException underTest = JsonParseException.newBuilder().build();

        assertThat(underTest.getErrorCode()).isEqualTo(JsonParseException.ERROR_CODE);
    }


    @Test
    public void getMessageReturnsNullIfNotExplicitlySet() {
        final JsonParseException underTest = JsonParseException.newBuilder().build();

        assertThat(underTest.getMessage()).isNull();
    }


    @Test
    public void getDescriptionReturnsDefaultIfNotExplicitlySet() {
        final JsonParseException underTest = JsonParseException.newBuilder().build();
        final Optional<String> description = underTest.getDescription();

        assertThat(description).isPresent();
        assertThat(description.get()).contains("Check if the JSON was valid");
    }


    @Test
    public void getDescriptionReturnsExpected() {
        final String description = "myDescription";
        final JsonParseException underTest = JsonParseException.newBuilder().description(description).build();

        assertThat(underTest.getDescription()).contains(description);
    }

}
