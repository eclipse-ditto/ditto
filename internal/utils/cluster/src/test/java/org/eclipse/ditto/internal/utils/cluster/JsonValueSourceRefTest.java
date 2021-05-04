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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.javadsl.TestKit;
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
        final var sourceRef = getSourceRef(List.of(JsonValue.of("Hello"), JsonValue.of(" "), JsonValue.of("Ditto!")));

        final var underTest = JsonValueSourceRef.of(sourceRef);

        assertThat(underTest.getSourceRef()).isEqualTo(sourceRef);
    }

    @Test
    public void getSourceReturnsNotNull() throws InterruptedException, ExecutionException, TimeoutException {
        final var jsonValues = List.of(JsonValue.of("Hello"), JsonValue.of(" "), JsonValue.of("Ditto!"));
        final var underTest = JsonValueSourceRef.of(getSourceRef(jsonValues));

        final var actualJsonValues = materializeSource(underTest.getSource());

        assertThat(actualJsonValues).isEqualTo(jsonValues);
    }

    @Test
    public void serializationWorks() throws InterruptedException, ExecutionException, TimeoutException {
        final var jsonValues = List.of(JsonValue.of("Hello!"), JsonObject.empty(), JsonArray.of(1, 2, true));
        final var underTest = JsonValueSourceRef.of(getSourceRef(jsonValues));
        final var messageReceiver = new TestKit(actorSystem);
        final var messageSender = new TestKit(actorSystem);
        final var messageReceiverRef = messageReceiver.getRef();

        messageReceiverRef.tell(underTest, messageSender.getRef());

        final var deserializedJsonValueSourceRef = messageReceiver.expectMsgClass(underTest.getClass());

        final var deserializedJsonValues = materializeSource(deserializedJsonValueSourceRef.getSource());

        assertThat(deserializedJsonValues).isEqualTo(jsonValues);
    }

    private static SourceRef<JsonValue> getSourceRef(final Iterable<JsonValue> jsonValues) {
        final var source = Source.from(jsonValues);
        return source.runWith(StreamRefs.sourceRef(), Materializer.apply(actorSystem));
    }

    private static List<JsonValue> materializeSource(final Source<JsonValue, NotUsed> source)
            throws InterruptedException, ExecutionException, TimeoutException {

        final var completionStage = source.runWith(Sink.seq(), actorSystem);
        final var completableFuture = completionStage.toCompletableFuture();
        return completableFuture.get(3, TimeUnit.SECONDS);
    }

}
