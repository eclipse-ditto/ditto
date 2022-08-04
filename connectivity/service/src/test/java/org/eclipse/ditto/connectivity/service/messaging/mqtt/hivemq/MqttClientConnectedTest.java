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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.ClassRule;
import org.junit.Test;

import akka.actor.ActorRef;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MqttClientConnected}.
 */
public final class MqttClientConnectedTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(MqttClientConnected.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MqttClientConnected.class)
                .usingGetClass()
                .withPrefabValues(ActorRef.class, getActorRef(), getActorRef())
                .verify();
    }

    private static ActorRef getActorRef() {
        final var testKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
        return testKit.getRef();
    }

    @Test
    public void getOriginFromInstanceWithNullOriginReturnsEmptyOptional() {
        final var underTest = MqttClientConnected.of(null);

        assertThat(underTest.getOrigin()).isEmpty();
    }

    @Test
    public void getOriginFromInstanceWithKnownOriginReturnsNonEmptyOptional() {
        final var origin = getActorRef();
        final var underTest = MqttClientConnected.of(origin);

        assertThat(underTest.getOrigin()).hasValue(origin);
    }

    @Test
    public void toStringOnInstanceWithNullOriginReturnsExpected() {
        final var underTest = MqttClientConnected.of(null);

        assertThat(underTest).hasToString("MqttClientConnected[origin=null]");
    }

    @Test
    public void toStringOnInstanceWithKnownOriginReturnsExpected() {
        final var origin = getActorRef();
        final var underTest = MqttClientConnected.of(origin);

        assertThat(underTest).hasToString("MqttClientConnected[origin=" + origin + "]");
    }

}