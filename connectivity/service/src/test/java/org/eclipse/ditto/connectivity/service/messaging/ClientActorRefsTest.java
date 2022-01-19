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

import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ClientActorRefs}.
 */
public final class ClientActorRefsTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE =
            ActorSystemResource.newInstance(ConfigFactory.load("client-actor-refs-test"));

    @Test
    public void serializationWorks() {
        final var underTest = ClientActorRefs.empty();
        underTest.add(ACTOR_SYSTEM_RESOURCE.newTestProbe().ref());
        underTest.add(ACTOR_SYSTEM_RESOURCE.newTestProbe().ref());
        final var messageReceiver = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var messageSender = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var messageReceiverRef = messageReceiver.getRef();

        messageReceiverRef.tell(underTest, messageSender.getRef());

        messageReceiver.expectMsg(underTest);
    }

}
