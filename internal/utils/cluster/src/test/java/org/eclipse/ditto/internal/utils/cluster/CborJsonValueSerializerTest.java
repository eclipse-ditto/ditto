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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.testkit.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link CborJsonValueSerializer}.
 */
public final class CborJsonValueSerializerTest {

    private static ExtendedActorSystem actorSystem;

    private CborJsonValueSerializer underTest;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = (ExtendedActorSystem) ActorSystem.create(CborJsonValueSerializer.class.getSimpleName());
    }

    @AfterClass
    public static void afterClass() {
        TestKit.shutdownActorSystem(actorSystem, FiniteDuration.apply(1, TimeUnit.SECONDS), false);
    }

    @Before
    public void setUp() {
        underTest = new CborJsonValueSerializer(actorSystem);
    }

    @Test
    public void identifierReturnsExpected() {
        assertThat(underTest.identifier()).isEqualTo(CborJsonValueSerializer.UNIQUE_IDENTIFIER);
    }

    @Test
    public void manifestForUnsupportedObject() {
        assertThatExceptionOfType(SerializerExceptions.NotSerializable.class)
                .isThrownBy(() -> underTest.manifest(new Object()))
                .withNoCause();
    }

    @Test
    public void manifestForSupportedObject() {
        final var manifest = underTest.manifest(JsonValue.of("Hallo"));

        assertThat(manifest).isEqualTo(CborJsonValueSerializer.JSON_VALUE_MANIFEST);
    }

    @Test
    public void toBinaryWithoutBufferWithUnsupportedObject() {
        assertThatExceptionOfType(SerializerExceptions.NotSerializable.class)
                .isThrownBy(() -> underTest.toBinary(new Object()))
                .withNoCause();
    }

    @Test
    public void toBinaryWithoutBufferWithJsonValue() throws IOException {
        final var jsonValue = JsonValue.of("Money can't buy life.");
        final var expectedSerializedJsonValue = serializeWithCborFactory(jsonValue);

        assertThat(underTest.toBinary(jsonValue)).isEqualTo(expectedSerializedJsonValue);
    }

    @Test
    public void toBinaryWithBufferWithUnsupportedObject() {
        assertThatExceptionOfType(SerializerExceptions.NotSerializable.class)
                .isThrownBy(() -> underTest.toBinary(new Object(), ByteBuffer.wrap(new byte[0])))
                .withNoCause();
    }

    @Test
    public void toBinaryWithTooSmallBufferWithJsonValue() {
        final var jsonValue = JsonValue.of("Giving up something that no longer serves a purpose, or " +
                "protects you, or helps you, isn’t giving up at all, it’s growing up.");
        final var byteBuffer = ByteBuffer.wrap(new byte[1]);

        assertThatExceptionOfType(SerializerExceptions.SerializationFailed.class)
                .isThrownBy(() -> underTest.toBinary(jsonValue, byteBuffer))
                .withCauseInstanceOf(BufferOverflowException.class);
    }

    @Test
    public void fromBinaryBytesWithUnsupportedManifestThrowsUnsupportedManifestException() {
        assertThatExceptionOfType(SerializerExceptions.UnsupportedManifest.class)
                .isThrownBy(() -> underTest.fromBinary(new byte[]{1}, "John_Titor"))
                .withNoCause();
    }

    @Test
    public void fromBinaryBytesWithValidManifestButInvalidBytesThrowsNotSerializableException() {
        final var bytes = new byte[]{98, -1, 2, 3};
        final var manifest = CborJsonValueSerializer.JSON_VALUE_MANIFEST;

        assertThatExceptionOfType(NotSerializableException.class)
                .isThrownBy(() -> underTest.fromBinary(bytes, manifest))
                .withMessage(manifest)
                .withNoCause();
    }

    @Test
    public void fromBinaryBytesWithValidArgumentsReturnsExpectedJsonValue() throws NotSerializableException {
        final var jsonObject = JsonObject.newBuilder()
                .set("type", "quoteOfTheDay")
                .set("value", JsonObject.newBuilder()
                        .set("date", JsonObject.newBuilder()
                                .set("year", 2004)
                                .set("month", 2)
                                .set("day", 19)
                                .build())
                        .set("quote", "Sometimes I think the surest sign that intelligent life exists elsewhere in " +
                                "the universe is that none of it has tried to contact us")
                        .set("origin", "Bill Watterson")
                        .set("makesSense", true)
                        .build())
                .build();
        final var serializedJsonObject = underTest.toBinary(jsonObject);

        final var deserializedJsonObject =
                underTest.fromBinary(serializedJsonObject, CborJsonValueSerializer.JSON_VALUE_MANIFEST);

        assertThat(deserializedJsonObject).isEqualTo(jsonObject);
    }

    private static byte[] serializeWithCborFactory(final JsonValue jsonValue) throws IOException {
        final var cborFactoryLoader = CborFactoryLoader.getInstance();
        final var cborFactory = cborFactoryLoader.getCborFactoryOrThrow();
        return cborFactory.toByteArray(jsonValue);
    }

}
