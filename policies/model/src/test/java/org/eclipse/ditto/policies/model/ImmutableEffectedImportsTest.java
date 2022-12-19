/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableEffectedImports}.
 */
public final class ImmutableEffectedImportsTest {

    private EffectedImports underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableEffectedImports.of(
            Arrays.asList(Label.of("IncludedEntry1"), Label.of("IncludedEntry2")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableEffectedImports.class,
                areImmutable(),
                provided(ImportedLabels.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEffectedImports.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final JsonObject effectedImportsJson = underTest.toJson();
        final EffectedImports effectedImports1 = ImmutableEffectedImports.fromJson(effectedImportsJson);

        assertThat(underTest).isEqualTo(effectedImports1);
    }

    @Test
    public void testGetImportedEntries() {
        assertThat(underTest.getImportedLabels()).isEqualTo(ImportedLabels.newInstance("IncludedEntry2",
                "IncludedEntry1"));

        assertThat(underTest.getImportedLabels()).isNotEqualTo(ImportedLabels.newInstance("ExcludedEntry1",
                "ExcludedEntry3"));
    }

}
