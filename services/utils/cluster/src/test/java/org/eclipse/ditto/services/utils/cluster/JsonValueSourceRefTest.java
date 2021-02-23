/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.TestKit;
import akka.testkit.TestProbe;
import nl.jqno.equalsverifier.EqualsVerifier;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link JsonValueSourceRef}.
 */
public final class JsonValueSourceRefTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        final var serializationTestConfig = ConfigFactory.load("serialization-test");
        actorSystem = ActorSystem.create(JsonValueSourceRefTest.class.getSimpleName(), serializationTestConfig);
    }

    @AfterClass
    public static void afterClass() {
        TestKit.shutdownActorSystem(actorSystem, FiniteDuration.apply(1, TimeUnit.SECONDS), false);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonValueSourceRef.class,
                areImmutable(),
                provided(SourceRef.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(JsonValueSourceRef.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithNullSourceRef() {
        assertThatNullPointerException()
                .isThrownBy(() -> JsonValueSourceRef.of(null))
                .withMessage("The sourceRef must not be null!")
                .withNoCause();
    }

    @Test
    public void getSourceRefReturnsExpected() {
        final var jsonValueSourceRef = getSourceRef();

        final var underTest = JsonValueSourceRef.of(jsonValueSourceRef);

        assertThat(underTest.getSourceRef()).isEqualTo(jsonValueSourceRef);
    }

    @Test
    public void getSourceReturnsNotNull() {
        final var underTest = JsonValueSourceRef.of(getSourceRef());

        assertThat(underTest.getSource()).isNotNull();
    }

    @Test
    public void serializationWorks() {
        final var underTest = JsonValueSourceRef.of(getSourceRef());

        final var messageSender = TestProbe.apply(actorSystem);
        final var messageReceiver = TestProbe.apply(actorSystem);

        final var senderRef = messageSender.ref();
        senderRef.tell(underTest, messageReceiver.ref());

        messageReceiver.expectMsgClass(JsonValueSourceRef.class);
    }

    private static SourceRef<JsonValue> getSourceRef() {
        final var source = Source.from(List.of(JsonValue.of("Hello"), JsonValue.of(" "), JsonValue.of("Ditto!")));
        return source.runWith(StreamRefs.sourceRef(), Materializer.apply(actorSystem));
    }

}