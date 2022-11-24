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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.serialization.SerializationExtension;
import akka.serialization.Serializers;
import akka.testkit.TestProbe;

/**
 * Tests {@link ClientActorPropsArgsSerializer}.
 */
public final class ClientActorPropsArgsSerializerTest {

    private final Config config = ConfigFactory.parseString("""
            akka.actor {
              enable-additional-serialization-bindings = on
              serializers {
                client-actor-props = "org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsArgsSerializer"
              }
              serialization-bindings {
                "org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsArgs" = "client-actor-props"
              }
            }
            """);

    private final ActorSystem actorSystem = ActorSystem.create("ClientActorPropsArgsSerializerTest", config);

    @After
    public void termiante() {
        actorSystem.terminate();
    }

    @Test
    public void serializeClientActorPropsArgs() {
        final var serialization = SerializationExtension.get(actorSystem);

        final var original = new ClientActorPropsArgs(
                TestConstants.createConnection().toBuilder().lifecycle(null).build(),
                TestProbe.apply(actorSystem).testActor(),
                TestProbe.apply(actorSystem).testActor(),
                DittoHeaders.newBuilder().randomCorrelationId().build(),
                ConfigFactory.parseString("dummy-key = 'dummy-value'")
        );

        final var serializer = serialization.findSerializerFor(original);
        final int id = serializer.identifier();
        final var manifest = Serializers.manifestFor(serializer, original);
        final var bytes = serialization.serialize(original).get();

        final var deserialized = serialization.deserialize(bytes, id, manifest).get();
        assertThat(deserialized).isEqualTo(original);
    }
}
