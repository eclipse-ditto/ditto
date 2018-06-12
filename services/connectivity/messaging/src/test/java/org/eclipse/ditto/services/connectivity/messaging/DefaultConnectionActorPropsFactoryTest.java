/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.connectivity.ConnectionType.AMQP_091;
import static org.eclipse.ditto.model.connectivity.ConnectionType.AMQP_10;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.remote.DaemonMsgCreate;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import akka.serialization.Serializer;
import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link DefaultConnectionActorPropsFactory}.
 */
public class DefaultConnectionActorPropsFactoryTest {

    private ActorSystem actorSystem;
    private Serialization serialization;
    private ConnectionActorPropsFactory underTest;

    @Before
    public void setUp() throws java.io.NotSerializableException {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        serialization = SerializationExtension.get(actorSystem);
        underTest = DefaultConnectionActorPropsFactory.getInstance();
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    /**
     * Tests serialization of props of AMQP_091 client actor. The props needs to be serializable because client actors
     * may be created on a different connectivity service instance using a local connection object.
     */
    @Test
    public void amqp091ActorPropsIsSerializable() {
        final Props props = underTest.getActorPropsForType(randomConnection(AMQP_091), actorSystem.deadLetters());
        final Object objectToSerialize = wrapForSerialization(props);
        final byte[] bytes = serialization.findSerializerFor(objectToSerialize).toBinary(objectToSerialize);
        final Object deserializedObject = serialization.deserialize(bytes, objectToSerialize.getClass()).get();

        assertThat(deserializedObject).isEqualTo(objectToSerialize);
    }

    /**
     * Tests serialization of props of AMQP_10 client actor. The props needs to be serializable because client actors
     * may be created on a different connectivity service instance using a local connection object.
     */
    @Test
    public void amqp10ActorPropsIsSerializable() {
        final Props props = underTest.getActorPropsForType(randomConnection(AMQP_10), actorSystem.deadLetters());
        final Object objectToSerialize = wrapForSerialization(props);
        final byte[] bytes = serialization.findSerializerFor(objectToSerialize).toBinary(objectToSerialize);
        final Object deserializedObject = serialization.deserialize(bytes, objectToSerialize.getClass()).get();

        assertThat(deserializedObject).isEqualTo(objectToSerialize);
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
                TestConstants.createConnection(TestConstants.createRandomConnectionId(), actorSystem);

        return ConnectivityModelFactory
                .newConnectionBuilder(template.getId(),
                        connectionType,
                        template.getConnectionStatus(),
                        template.getUri(),
                        template.getAuthorizationContext())
                .sources(template.getSources())
                .targets(template.getTargets())
                .build();
    }
}
