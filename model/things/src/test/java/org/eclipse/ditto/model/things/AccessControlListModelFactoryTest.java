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
package org.eclipse.ditto.model.things;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Unit test for {@link ThingsModelFactory}.
 */
public final class AccessControlListModelFactoryTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(AccessControlListModelFactory.class, areImmutable());
    }


    @Test(expected = UnsupportedOperationException.class)
    public void unmodifiablePermissionsAreUnmodifiable() {
        final Permissions unmodifiablePermissions =
                AccessControlListModelFactory.newUnmodifiablePermissions(Permission.READ, Permission.WRITE);
        unmodifiablePermissions.add(Permission.ADMINISTRATE);
    }

}
