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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
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
    }

}
