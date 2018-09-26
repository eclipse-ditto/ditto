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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.messages.MessageDirection.FROM;
import static org.eclipse.ditto.model.messages.MessageDirection.TO;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.OptionalAssert;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableMessagePath}.
 */
public class ImmutableMessagePathTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableMessagePath.class, areImmutable(), provided(JsonPointer.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableMessagePath.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullJsonPointer() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableMessagePath.of(null))
                .withMessage("The %s must not be null!", "JSON pointer")
                .withNoCause();
    }

    @Test
    public void parseDirection() {
        assertDirection("/outbox/message/ask").contains(FROM);
        assertDirection("/attributes/hello").isEmpty();
        assertDirection("/features/water-tank/properties/temperature").isEmpty();
        assertDirection("/features/water-tank/inbox/messages/heatUp").contains(TO);
    }

    @Test
    public void parseFeatureId() {
        assertFeatureId("/outbox/message/ask").isEmpty();
        assertFeatureId("/attributes/hello").isEmpty();
        assertFeatureId("/features/water-tank/properties/temperature").contains("water-tank");
        assertFeatureId("features/water-tank/inbox/messages/heatUp").contains("water-tank");
    }

    private static OptionalAssert<MessageDirection> assertDirection(final String jsonPointer) {
        return assertThat(ImmutableMessagePath.of(JsonPointer.of(jsonPointer)).getDirection());
    }

    private static OptionalAssert<String> assertFeatureId(final String jsonPointer) {
        return assertThat(ImmutableMessagePath.of(JsonPointer.of(jsonPointer)).getFeatureId());
    }
}
