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
package org.eclipse.ditto.policies.model.enforcers.tree;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EffectedResources}.
 */
public class EffectedResourcesTest {

    private static final Set<TreeBasedPolicyEnforcer.PointerAndPermission> KNOWN_RESOURCES =
            Collections.singleton(new TreeBasedPolicyEnforcer.PointerAndPermission(
                    JsonFactory.newPointer("/"), "WRITE"));

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EffectedResources.class) //
                .withRedefinedSuperclass() //
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullGrantedResources() {
        EffectedResources.of(null, KNOWN_RESOURCES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullRevokedResources() {
        EffectedResources.of(KNOWN_RESOURCES, null);
    }

}
