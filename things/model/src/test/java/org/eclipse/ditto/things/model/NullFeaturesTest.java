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
