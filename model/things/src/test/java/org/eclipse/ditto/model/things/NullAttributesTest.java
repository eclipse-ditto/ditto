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
package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link NullAttributes}.
 */
public final class NullAttributesTest {

    private Attributes underTest;


    @Before
    public void setUp() {
        underTest = NullAttributes.newInstance();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(NullAttributes.class, areImmutable(), provided(JsonObject.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(NullAttributes.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void nullAttributesIsEmpty() {
        assertThat(underTest).isEmpty();
    }


    @Test
    public void nullAttributesIsJsonNull() {
        assertThat(underTest.isNull()).isTrue();
    }

    @Test
    public void nullAttributesIsJsonObject() {
        assertThat(underTest.isObject()).isTrue();
    }

    @Test
    public void nullAttributesAsJsonObject() {
        assertThat(underTest.asObject()).isEqualTo(JsonFactory.nullObject());
    }

    @Test
    public void createInstanceReturnsTheExpectedJson() {
        assertThat(underTest.toJsonString()).isEqualTo("null");
    }

    @Test
    public void nullAttributesIsNothing() {
        assertThat(underTest).isNotBoolean();
        assertThat(underTest).isNotNumber();
        assertThat(underTest).isNotString();
        assertThat(underTest).isNotArray();
    }
}
