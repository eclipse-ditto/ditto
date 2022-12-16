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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableImportedLabels}.
 */
public final class ImmutableImportedLabelsTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableImportedLabels.class,
                areImmutable(),
                provided(Label.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableImportedLabels.class)
                .usingGetClass()
                .withNonnullFields("entryLabels")
                .verify();
    }

    @Test
    public void createJsonRepresentationOfEmptyLabels() {
        final JsonValue actualJsonValue = ImportedLabels.none().toJson();

        assertThat(actualJsonValue).isEqualTo(JsonArray.newBuilder().build());
    }

    @Test
    public void testContains() {
        final ImportedLabels labels = ImportedLabels.newInstance("label1", "label2", "label4");

        assertThat(labels.contains("label2", "label4")).isTrue();
        assertThat(labels.contains("label3")).isFalse();
        assertThat(labels.contains(ImportedLabels.newInstance("label1", "label2"))).isTrue();
        assertThat(labels.contains(ImportedLabels.newInstance("label5", "label6"))).isFalse();
    }
}
