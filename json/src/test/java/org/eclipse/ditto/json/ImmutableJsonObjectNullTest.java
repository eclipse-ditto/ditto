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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;

import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonObjectNull}.
 */
public final class ImmutableJsonObjectNullTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonObjectNull.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<String> red = new SoftReference<>("red");
        final SoftReference<String> black = new SoftReference<>("black");

        EqualsVerifier.forClass(ImmutableJsonObjectNull.class) //
                .withIgnoredFields("stringRepresentation")
                .withPrefabValues(SoftReference.class, red, black) //
                .withRedefinedSuperclass() //
                .verify();

        final ImmutableJsonObjectNull green = ImmutableJsonObjectNull.newInstance();
        final ImmutableJsonObjectNull blue = ImmutableJsonObjectNull.newInstance();

        DittoJsonAssertions.assertThat(green).isEqualTo(blue);
    }

}
