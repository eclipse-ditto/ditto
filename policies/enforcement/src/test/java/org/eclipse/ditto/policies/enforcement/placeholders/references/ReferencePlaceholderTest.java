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

package org.eclipse.ditto.policies.enforcement.placeholders.references;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.eclipse.ditto.placeholders.PlaceholderReferenceNotSupportedException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.policies.enforcement.placeholders.references.ReferencePlaceholder}.
 */
public class ReferencePlaceholderTest {

    private static final ThingId THING_ID = ThingId.of("namespace:myThing");

    @Test
    public void fromCharSequence() {
        final Optional<ReferencePlaceholder> referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ref:things/" + THING_ID + "/policyId}}");

        assertThat(referencePlaceholder).isPresent();
        assertThat(referencePlaceholder.get().getReferencedEntityId().toString()).isEqualTo(THING_ID.toString());
        DittoJsonAssertions.assertThat(referencePlaceholder.get().getReferencedField())
                .isEqualTo(                JsonPointer.of("policyId"));
        assertThat(referencePlaceholder.get().getReferencedEntityType()).isEqualTo(
                ReferencePlaceholder.ReferencedEntityType.THINGS);
    }

    @Test
    public void fromCharSequenceWithSpacesAfterBraces() {
        final Optional<ReferencePlaceholder> referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}");

        assertThat(referencePlaceholder).isPresent();
        assertThat(referencePlaceholder.get().getReferencedEntityId().toString()).isEqualTo(THING_ID.toString());
        DittoJsonAssertions.assertThat(referencePlaceholder.get().getReferencedField())
                .isEqualTo(JsonPointer.of("policyId"));
        assertThat(referencePlaceholder.get().getReferencedEntityType()).isEqualTo(
                ReferencePlaceholder.ReferencedEntityType.THINGS);
    }

    @Test
    public void fromCharSequenceWithInvalidPlaceholderIsEmpty() {
        assertThat(ReferencePlaceholder.fromCharSequence("{{things/" + THING_ID + "/policyId}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence("{{ref:things" + THING_ID + "/policyId}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence("{{ref:things/ " + THING_ID + "/policyId}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence("{{ref:things/" + THING_ID + "}}")).isNotPresent();

        assertThat(ReferencePlaceholder.fromCharSequence(null)).isNotPresent();
    }

    @Test
    public void fromCharSequenceWithDeepPointer() {
        final Optional<ReferencePlaceholder> referencePlaceholder =
                ReferencePlaceholder.fromCharSequence(
                        "{{ref:things/" + THING_ID + "/features/properties/policyFeature/policyId}}");

        assertThat(referencePlaceholder).isPresent();
        assertThat(referencePlaceholder.get().getReferencedEntityId().toString()).isEqualTo(THING_ID.toString());
        DittoJsonAssertions.assertThat(referencePlaceholder.get().getReferencedField()).isEqualTo(
                JsonPointer.of("features/properties/policyFeature/policyId"));
        assertThat(referencePlaceholder.get().getReferencedEntityType()).isEqualTo(
                ReferencePlaceholder.ReferencedEntityType.THINGS);
    }

    @Test
    public void fromCharSequenceWithUnsupportedEntityTypeThrowsException() {
        assertThatThrownBy(() -> ReferencePlaceholder.fromCharSequence("{{ref:topologies/namespace:thingid/policyId}}"))
                .isInstanceOf(PlaceholderReferenceNotSupportedException.class);
    }

}
