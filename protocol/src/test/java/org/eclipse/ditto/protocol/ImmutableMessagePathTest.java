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
package org.eclipse.ditto.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.messages.model.MessageDirection.FROM;
import static org.eclipse.ditto.messages.model.MessageDirection.TO;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.OptionalAssert;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.messages.model.MessageDirection;
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
