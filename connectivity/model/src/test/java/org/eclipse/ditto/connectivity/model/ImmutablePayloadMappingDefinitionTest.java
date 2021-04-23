/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.model;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutablePayloadMappingDefinition}.
 */
public class ImmutablePayloadMappingDefinitionTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePayloadMappingDefinition.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePayloadMappingDefinition.class, areImmutable(),
                provided(MappingContext.class).areAlsoImmutable(),
                assumingFields("definitions").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }
}
