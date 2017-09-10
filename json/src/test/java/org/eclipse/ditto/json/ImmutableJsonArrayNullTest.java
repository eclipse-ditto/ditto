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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonArrayNull}.
 */
public final class ImmutableJsonArrayNullTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonArrayNull.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<String> red = new SoftReference<>("red");
        final SoftReference<String> black = new SoftReference<>("black");

        EqualsVerifier.forClass(ImmutableJsonArrayNull.class) //
                .withIgnoredFields("stringRepresentation")
                .withPrefabValues(SoftReference.class, red, black) //
                .withRedefinedSuperclass() //
                .verify();

        final ImmutableJsonArrayNull green = ImmutableJsonArrayNull.newInstance();
        final ImmutableJsonArrayNull blue = ImmutableJsonArrayNull.newInstance();

        Assertions.assertThat(green).isEqualTo(blue);

        final ImmutableJsonObjectNull nullObject = ImmutableJsonObjectNull.newInstance();

        assertThat(green.equals(nullObject)).isTrue();
    }

}
