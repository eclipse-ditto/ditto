/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializers;
import org.apache.pekko.serialization.jackson.JacksonCborSerializer;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link SignalAccepted}.
 */
public final class SignalAcceptedTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create(SignalAcceptedTest.class.getSimpleName(),
                ConfigFactory.load("serialization-test"));
    }

    @AfterClass
    public static void afterClass() {
        TestKit.shutdownActorSystem(actorSystem, FiniteDuration.apply(1, TimeUnit.SECONDS), false);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SignalAccepted.class).verify();
    }

    @Test
    public void serializationRoundTripUsesJacksonCbor() {
        final var serialization = SerializationExtension.get(actorSystem);
        final var underTest = new SignalAccepted();

        final var serializer = serialization.findSerializerFor(underTest);
        final byte[] bytes = serialization.serialize(underTest).get();
        final Object deserialized = serialization.deserialize(bytes, serializer.identifier(),
                Serializers.manifestFor(serializer, underTest)).get();

        assertThat(serializer).isInstanceOf(JacksonCborSerializer.class);
        assertThat(deserialized).isEqualTo(underTest);
    }
}
