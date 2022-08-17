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
package org.eclipse.ditto.things.model;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Unit test for {@link NullFeatureProperties}.
 */
public final class NullFeaturePropertiesTest {

    private FeatureProperties underTest;

    @Before
    public void setUp() {
        underTest = NullFeatureProperties.newInstance();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(NullFeatureProperties.class)
                .usingGetClass()
                .verify();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(NullFeatureProperties.class, areImmutable(), provided(JsonObject.class).isAlsoImmutable());
    }


    @Test
    public void nullFeaturePropertiesIsJsonNull() {
        assertThat(underTest.isNull()).isTrue();
    }

    @Test
    public void nullFeaturePropertiesIsJsonObject() {
        assertThat(underTest.isObject()).isTrue();
    }

    @Test
    public void nullFeaturePropertiesAsJsonObject() {
        assertThat(underTest.asObject()).isEqualTo(JsonFactory.nullObject());
    }

    @Test
    public void createInstanceReturnsTheExpectedJson() {
        final FeatureProperties properties = NullFeatureProperties.newInstance();

        assertThat(properties.toJsonString()).isEqualTo("null");
    }

    @Test
    public void nullFeaturePropertiesIsNothing() {
        assertThat(underTest).isNotBoolean()
                .isNotNumber()
                .isNotString()
                .isNotArray();
    }

}
