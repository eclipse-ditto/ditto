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
