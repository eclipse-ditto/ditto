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

package org.eclipse.ditto.services.concierge.enforcement.placeholders.references;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderReferenceNotSupportedException;
import org.junit.Test;

/**
 * Tests {@link ReferencePlaceholder}.
 */
public class ReferencePlaceholderTest {

    @Test
    public void fromCharSequence() {
        final Optional<ReferencePlaceholder> referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ref:things/namespace:thingid/policyId}}");

        assertThat(referencePlaceholder).isPresent();
        assertThat(referencePlaceholder.get().getReferencedEntityId()).isEqualTo("namespace:thingid");
        assertThat(referencePlaceholder.get().getReferencedFieldSelector()).isEqualTo(
                JsonFieldSelector.newInstance("policyId"));
        assertThat(referencePlaceholder.get().getReferencedEntityType()).isEqualTo(
                ReferencePlaceholder.ReferencedEntityType.THINGS);
    }

    @Test
    public void fromCharSequenceWithSpacesAfterBraces() {
        final Optional<ReferencePlaceholder> referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/namespace:thingid/policyId }}");

        assertThat(referencePlaceholder).isPresent();
        assertThat(referencePlaceholder.get().getReferencedEntityId()).isEqualTo("namespace:thingid");
        assertThat(referencePlaceholder.get().getReferencedFieldSelector()).isEqualTo(
                JsonFieldSelector.newInstance("policyId"));
        assertThat(referencePlaceholder.get().getReferencedEntityType()).isEqualTo(
                ReferencePlaceholder.ReferencedEntityType.THINGS);
    }

    @Test
    public void fromCharSequenceWithInvalidPlaceholderIsEmpty() {
        assertThat(ReferencePlaceholder.fromCharSequence("{{things/namespace:thingid/policyId}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence("{{ref:thingsnamespace:thingid/policyId}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence("{{ref:things/ namespace:thingid/policyId}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence("{{ref:things/namespace:thingid}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence("{{ref:things/namespace:thingid/policyId/}}")).isNotPresent();
        assertThat(ReferencePlaceholder.fromCharSequence(
                "{{ref:things/namespace:thingid/policyId/forbidden}}")).isNotPresent();

        assertThat(ReferencePlaceholder.fromCharSequence(null)).isNotPresent();
    }

    @Test
    public void fromCharSequenceWithUnsupportedEntityTypeThrowsException() {
        assertThatThrownBy(() -> ReferencePlaceholder.fromCharSequence("{{ref:topologies/namespace:thingid/policyId}}"))
                .isInstanceOf(GatewayPlaceholderReferenceNotSupportedException.class);
    }
}