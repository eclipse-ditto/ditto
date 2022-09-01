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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.entity.id.restriction.LengthRestrictionTestBase;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Unit test for {@link PoliciesModelFactoryTest}.
 */
public final class PoliciesModelFactoryTest extends LengthRestrictionTestBase {

    @Test
    public void resourceKeysFromDifferentInstantiationsAreEqual() {
        final ResourceKey key1 = PoliciesModelFactory.newResourceKey("thing:/foo/bar");
        final ResourceKey key2 = PoliciesModelFactory.newResourceKey("thing", JsonFactory.newPointer("/foo/bar"));
        final ResourceKey key3 = PoliciesModelFactory.newResourceKey(JsonPointer.of("thing:/foo/bar"));
        final ResourceKey key4 =
                PoliciesModelFactory.newResourceKey(JsonPointer.of("thing:").append(JsonPointer.of("foo/bar")));

        assertThat(key1).isEqualTo(key2)
                .isEqualTo(key3)
                .isEqualTo(key4);
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void createInvalidResourceKey() {
        final String invalidResourceKey = "thing:/foo/bar\u0001";
        PoliciesModelFactory.newResourceKey(invalidResourceKey);
    }

    @Test
    public void createValidMaxLengthResourceKey() {
        PoliciesModelFactory.newResourceKey(generateStringWithMaxLength("thing:"));
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void createTooLargeResourceKey() {
        PoliciesModelFactory.newResourceKey(generateStringExceedingMaxLength("thing:"));
    }

}
