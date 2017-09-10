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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link NullFeatures}.
 */
public final class NullFeaturesTest {


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(NullFeatures.class).verify();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(NullFeatures.class, areImmutable());
    }

    @Test
    public void createInstanceReturnsTheExceptedJson() {
        final NullFeatures features = NullFeatures.newInstance();

        assertThat(features.toJsonString()).isEqualTo("null");
    }

}
