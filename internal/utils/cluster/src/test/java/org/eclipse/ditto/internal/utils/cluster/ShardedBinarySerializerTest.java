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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.serialization.SerializationExtension;
import akka.serialization.Serializers;

/**
 * Tests {@link ShardedBinarySerializer}.
 */
public final class ShardedBinarySerializerTest {

    private final Config config = ConfigFactory.parseString("""
            akka.actor {
              enable-additional-serialization-bindings = on
              serializers {
                sharded-binary-envelope = "org.eclipse.ditto.internal.utils.cluster.ShardedBinarySerializer"
              }
              serialization-bindings {
                "org.eclipse.ditto.internal.utils.cluster.ShardedBinaryEnvelope" = "sharded-binary-envelope"
              }
            }
            """);

    private final ActorSystem actorSystem = ActorSystem.create("ShardedBinarySerializerTest", config);

    @After
    public void termiante() {
        actorSystem.terminate();
    }

    @Test
    public void serializePoisonPill() {
        final var serialization = SerializationExtension.get(actorSystem);
        final var message = PoisonPill.getInstance();

        final var original = new ShardedBinaryEnvelope(message, "hoo");

        final var serializer = serialization.findSerializerFor(original);
        final int id = serializer.identifier();
        final var manifest = Serializers.manifestFor(serializer, original);
        final var bytes = serialization.serialize(original).get();

        final var deserialized = serialization.deserialize(bytes, id, manifest).get();
        assertThat(deserialized).isEqualTo(original);

        final var deserializedEnvelope = (ShardedBinaryEnvelope) deserialized;
        assertThat(deserializedEnvelope.message()).isEqualTo(message);
        assertThat(deserializedEnvelope.entityName()).isEqualTo("hoo");
    }
}
