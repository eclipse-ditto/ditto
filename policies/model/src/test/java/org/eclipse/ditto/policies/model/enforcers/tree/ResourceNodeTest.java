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

import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.enforcers.tree.ResourceNode}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ResourceNodeTest {

    private static final String KNOWN_NAME = "name";

    private static final EffectedPermissions KNOWN_PERMISSIONS =
            EffectedPermissions.newInstance(Collections.singleton("READ"),
                    Collections.singleton("WRITE"));

    @Mock
    private static PolicyTreeNode knownParent;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ResourceNode.class)
                .withRedefinedSuperclass()
                .suppress(Warning.NONFINAL_FIELDS)
                .withIgnoredFields("absolutePointer")
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullParent() {
        ResourceNode.of(null, KNOWN_NAME, KNOWN_PERMISSIONS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullName() {
        ResourceNode.of(knownParent, null, KNOWN_PERMISSIONS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPermissions() {
        ResourceNode.of(knownParent, KNOWN_NAME, null);
    }

}
