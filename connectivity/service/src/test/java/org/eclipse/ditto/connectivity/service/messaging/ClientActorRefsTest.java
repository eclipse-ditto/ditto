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

import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

public final class ClientActorRefsTest {

    @ClassRule
    public static ActorSystemResource actorSystemResource =
            ActorSystemResource.newInstance(ConfigFactory.load("client-actor-refs-test"));

    @Test
    public void serializationWorks() {
        final ActorSystem actorSystem = actorSystemResource.getActorSystem();
        final ActorRef ref1 = actorSystemResource.newTestProbe().ref();
        final ActorRef ref2 = actorSystemResource.newTestProbe().ref();
        final var underTest = ClientActorRefs.empty();
        underTest.add(ref1);
        underTest.add(ref2);
        final var messageReceiver = new TestKit(actorSystem);
        final var messageSender = new TestKit(actorSystem);
        final var messageReceiverRef = messageReceiver.getRef();

        messageReceiverRef.tell(underTest, messageSender.getRef());

        final var receivedClientActorRefs = messageReceiver.expectMsgClass(underTest.getClass());

        assertThat(receivedClientActorRefs).isEqualTo(underTest);
    }

}
