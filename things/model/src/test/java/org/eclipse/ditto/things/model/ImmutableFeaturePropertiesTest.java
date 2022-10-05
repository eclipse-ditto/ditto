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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.id.restriction.LengthRestrictionTestBase;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableFeatureProperties}.
 */
public final class ImmutableFeaturePropertiesTest extends LengthRestrictionTestBase {


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFeatureProperties.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFeatureProperties.class, areImmutable(),
                provided(JsonObject.class).isAlsoImmutable());
    }

    @Test
    public void createInstanceReturnsTheExpectedJson() {
        final FeatureProperties properties = ImmutableFeatureProperties.empty();

        assertThat(properties.toJsonString()).isEqualTo("{}");
    }

    @Test
    public void ensureFeaturesToBuilderWorks() {
        assertThat(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES).isEqualTo(
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toBuilder().build());
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidPropertyKey() {
        final String invalidPropertyKey = "invalid/";
        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.setValue(invalidPropertyKey, "invalidPropertyKey")
                .toBuilder()
                .build();
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidNestedPropertyKey() {
        final String validPropertyKey = "valid";
        final JsonObject invalidJsonObject = JsonObject.newBuilder()
                .set("foo/", "bar")
                .build();
        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.setValue(validPropertyKey, invalidJsonObject)
                .toBuilder()
                .build();
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidNestedNestedPropertyKey() {
        final String validPropertyKey = "valid";
        final JsonObject invalidJsonObject = JsonObject.newBuilder()
                .set("foo/", "bar")
                .build();
        final JsonObject validJsonObject = JsonObject.newBuilder()
                .set("foo", "bar")
                .set("invalid", invalidJsonObject)
                .build();
        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.setValue(validPropertyKey, validJsonObject)
                .toBuilder()
                .build();
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createTooLargePropertyKey() {
        final String tooLargePropertyKey = generateStringExceedingMaxLength();
        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.setValue(tooLargePropertyKey, "tooLargePropertyKey")
                .toBuilder()
                .build();
    }
}
