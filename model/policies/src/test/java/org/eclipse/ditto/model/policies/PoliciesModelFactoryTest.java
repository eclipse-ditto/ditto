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
package org.eclipse.ditto.model.policies;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

/**
 * Unit test for {@link PoliciesModelFactoryTest}.
 */
public final class PoliciesModelFactoryTest {

    @Test
    public void resourceKeysFromDifferentInstantiationsAreEqual() {
        final ResourceKey key1 = PoliciesModelFactory.newResourceKey("thing:/foo/bar");
        final ResourceKey key2 = PoliciesModelFactory.newResourceKey("thing", JsonFactory.newPointer("/foo/bar"));

        assertThat(key1).isEqualTo(key2);
    }

}
