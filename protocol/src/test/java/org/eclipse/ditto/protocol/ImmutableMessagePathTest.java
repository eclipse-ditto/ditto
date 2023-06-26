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

import org.assertj.core.api.AbstractBooleanAssert;
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

    @Test
    public void parseMessageSubject() {
        assertMessageSubject("/outbox/message/ask").contains("ask");
        assertMessageSubject("/attributes/hello").isEmpty();
        assertMessageSubject("/features/water-tank/properties/temperature").isEmpty();
        assertMessageSubject("features/water-tank/inbox/messages/heatUp").contains("heatUp");
        assertMessageSubject("/features/water-tank/inbox/messages/heatUp/subMsg").contains("heatUp/subMsg");
    }

    @Test
    public void parseIsInboxOutboxMessage() {
        assertIsInboxOutboxMessage("/outbox/message/ask").isTrue();
        assertIsInboxOutboxMessage("/attributes/hello").isFalse();
        assertIsInboxOutboxMessage("/features/water-tank/properties/temperature").isFalse();
        assertIsInboxOutboxMessage("features/water-tank/inbox/messages/heatUp").isTrue();
    }

    private static OptionalAssert<MessageDirection> assertDirection(final String jsonPointer) {
        return assertThat(ImmutableMessagePath.of(JsonPointer.of(jsonPointer)).getDirection());
    }

    private static OptionalAssert<String> assertFeatureId(final String jsonPointer) {
        return assertThat(ImmutableMessagePath.of(JsonPointer.of(jsonPointer)).getFeatureId());
    }

    private static OptionalAssert<String> assertMessageSubject(final String jsonPointer) {
        return assertThat(ImmutableMessagePath.of(JsonPointer.of(jsonPointer)).getMessageSubject());
    }

    private static AbstractBooleanAssert<?> assertIsInboxOutboxMessage(final String jsonPointer) {
        return assertThat(ImmutableMessagePath.of(JsonPointer.of(jsonPointer)).isInboxOutboxMessage());
    }
}
