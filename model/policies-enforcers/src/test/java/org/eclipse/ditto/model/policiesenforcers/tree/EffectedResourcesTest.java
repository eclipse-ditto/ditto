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
package org.eclipse.ditto.model.policiesenforcers.tree;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EffectedResources}.
 */
public class EffectedResourcesTest {

    private static final Set<TreeBasedPolicyEnforcer.PointerAndPermission> KNOWN_RESOURCES =
            Collections.singleton(new TreeBasedPolicyEnforcer.PointerAndPermission(
                    JsonFactory.newPointer("/"), "WRITE"));

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(EffectedResources.class, areImmutable(), provided(JsonPointer.class).isAlsoImmutable(),
                provided(TreeBasedPolicyEnforcer.PointerAndPermission.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EffectedResources.class) //
                .withRedefinedSuperclass() //
                .verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullGrantedResources() {
        EffectedResources.of(null, KNOWN_RESOURCES);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullRevokedResources() {
        EffectedResources.of(KNOWN_RESOURCES, null);
    }

}
