/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.base.headers.entitytag;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link EntityTagMatchers}.
 */
public class EntityTagMatchersTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EntityTagMatchers.class)
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTagMatchers.class, areImmutable());
    }
}
