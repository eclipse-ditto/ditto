/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import com.mongodb.client.result.DeleteResult;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CleanupResult}.
 */
public final class CleanupResultTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(CleanupResult.class, areImmutable(),
                provided(DeleteResult.class, SnapshotRevision.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CleanupResult.class)
                .usingGetClass()
                .verify();
    }

}
