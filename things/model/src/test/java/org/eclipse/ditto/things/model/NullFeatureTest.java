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

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link NullFeature}.
 */
public final class NullFeatureTest {

    private static final String KNOWN_FEATURE_ID = "Feature ID";


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(NullFeature.class).verify();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(NullFeature.class, areImmutable(), provided(JsonSchemaVersion.class).isAlsoImmutable());
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        NullFeature.of(null);
    }

    @Test
    public void createInstanceReturnsTheExceptedJson() {
        final NullFeature feature = NullFeature.of(KNOWN_FEATURE_ID);

        assertThat(feature.toJsonString()).isEqualTo("null");
        assertThat(feature.getProperties()).isEmpty();
        assertThat(feature.getDesiredProperties()).isEmpty();
    }

}
