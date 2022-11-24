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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.model.ConnectionType.AMQP_091;
import static org.eclipse.ditto.connectivity.model.ConnectionType.AMQP_10;
import static org.eclipse.ditto.connectivity.model.ConnectionType.HONO;
import static org.eclipse.ditto.connectivity.model.ConnectionType.KAFKA;
import static org.eclipse.ditto.connectivity.model.ConnectionType.MQTT;
import static org.eclipse.ditto.connectivity.model.ConnectionType.MQTT_5;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.remote.DaemonMsgCreate;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;

/**
 * Unit tests for {@link DefaultClientActorPropsFactory}.
 */
public final class DefaultClientActorPropsFactoryTest extends WithMockServers {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(TestConstants.CONFIG);

    private ActorSystem actorSystem;
    private Serialization serialization;
    private ClientActorPropsFactory underTest;

    @Before
    public void setUp() {
        actorSystem = actorSystemResource.getActorSystem();
        serialization = SerializationExtension.get(actorSystem);
        underTest =
                ClientActorPropsFactory.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()));
    }

    /**
     * Tests serialization of props of AMQP_091 client actor.
     * The props needs to be serializable because client actors may be created on a different connectivity service
     * instance using a local connection object.
     */
    @Test
    public void amqp091ActorPropsIsSerializable() {
        actorPropsIsSerializableAndEqualDeserializedObject(AMQP_091);
    }

    /**
     * Tests serialization of props of AMQP_10 client actor.
     * The props needs to be serializable because client actors may be created on a different connectivity service
     * instance using a local connection object.
     */
    @Test
    public void amqp10ActorPropsIsSerializable() {
        actorPropsIsSerializableAndEqualDeserializedObject(AMQP_10);
    }

    /**
     * Tests serialization of props of MQTT client actor. The props needs to be serializable because client actors
     * may be created on a different connectivity service instance using a local connection object.
     */
    @Test
    public void mqttActorPropsIsSerializable() {
        actorPropsIsSerializableAndEqualDeserializedObject(MQTT);
    }

    /**
     * Tests serialization of props of MQTT client actor. The props needs to be serializable because client actors
     * may be created on a different connectivity service instance using a local connection object.
     */
    @Test
    public void mqtt5ActorPropsIsSerializable() {
        actorPropsIsSerializableAndEqualDeserializedObject(MQTT_5);
    }

    /**
     * Tests serialization of props of Kafka client actor. The props needs to be serializable because client actors
     * may be created on a different connectivity service instance using a local connection object.
     */
    @Test
    @SuppressWarnings("squid:S2699")
    public void kafkaActorPropsIsSerializable() {
        actorPropsIsSerializable(KAFKA);
    }

    /**
     * Tests serialization of props of Hono client actor. The props needs to be serializable because client actors
     * may be created on a different connectivity service instance using a local connection object.
     */
    @Test
    @SuppressWarnings("squid:S2699")
    public void honoActorPropsIsSerializable() {
        actorPropsIsSerializable(HONO);
    }

    private void actorPropsIsSerializable(final ConnectionType connectionType) {
        final Props props = underTest.getActorPropsForType(randomConnection(connectionType), actorSystem.deadLetters(),
                actorSystem.deadLetters(), actorSystem, DittoHeaders.empty(), ConfigFactory.empty());
        final Object objectToSerialize = wrapForSerialization(props);
        serializeAndDeserialize(objectToSerialize);
    }

    private void actorPropsIsSerializableAndEqualDeserializedObject(final ConnectionType connectionType) {
        final Props props = underTest.getActorPropsForType(randomConnection(connectionType), actorSystem.deadLetters(),
                actorSystem.deadLetters(), actorSystem, DittoHeaders.empty(), ConfigFactory.empty());
        final Object objectToSerialize = wrapForSerialization(props);
        final Object deserializedObject = serializeAndDeserialize(objectToSerialize);

        assertThat(deserializedObject).isEqualTo(objectToSerialize);
    }

    private Object serializeAndDeserialize(final Object objectToSerialize) {
        final byte[] bytes = serialization.findSerializerFor(objectToSerialize).toBinary(objectToSerialize);
        return serialization.deserialize(bytes, objectToSerialize.getClass()).get();
    }

    /**
     * Wrap Props in an object with a reasonable Akka serializer, namely one that applies our configured
     * serializer on each argument of Props. For Akka 2.5.13, that object belongs to the Akka-internal class
     * DaemonMsgCreate. The class may change in future versions of Akka.
     */
    private Object wrapForSerialization(final Props props) {
        final String actorClassNameAsPath = props.actorClass().getSimpleName();
        return DaemonMsgCreate.apply(props, props.deploy(), actorClassNameAsPath, actorSystem.deadLetters());
    }

    private Connection randomConnection(final ConnectionType connectionType) {
        final Connection template =
                TestConstants.createConnection(TestConstants.createRandomConnectionId());

        return ConnectivityModelFactory
                .newConnectionBuilder(template.getId(),
                        connectionType,
                        template.getConnectionStatus(),
                        template.getUri())
                .sources(template.getSources())
                .targets(template.getTargets())
                .build();
    }

}
