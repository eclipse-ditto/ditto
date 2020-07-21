/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ReceiveTimeout;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.ResponseCollectorActor}.
 */
public final class ResponseCollectorActorTest {

    private static final CommandResponse<?> RESPONSE = DeleteThingResponse.of(ThingId.dummy(), DittoHeaders.empty());

    private ActorSystem actorSystem;

    @Before
    public void init() {
        actorSystem = ActorSystem.create();
    }

    @After
    public void cleanUp() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void timeout() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(ResponseCollectorActor.props(Duration.ofMinutes(1L))));
            underTest.tell(ResponseCollectorActor.query(), getRef());
            underTest.tell(ReceiveTimeout.getInstance(), getRef());
            final ResponseCollectorActor.Output output = expectMsgClass(ResponseCollectorActor.Output.class);
            assertThat(output.allExpectedResponsesArrived()).isFalse();
            assertThat(output.getCommandResponses()).isEmpty();
            expectTerminated(underTest);
        }};
    }

    @Test
    public void allCollected() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(ResponseCollectorActor.props(Duration.ofMinutes(1L))));
            underTest.tell(ResponseCollectorActor.query(), getRef());
            underTest.tell(ResponseCollectorActor.setCount(2), getRef());
            underTest.tell(RESPONSE, getRef());
            underTest.tell(RESPONSE, getRef());
            final ResponseCollectorActor.Output output = expectMsgClass(ResponseCollectorActor.Output.class);
            assertThat(output.allExpectedResponsesArrived()).isTrue();
            assertThat(output.getCommandResponses()).containsExactly(RESPONSE, RESPONSE);
            expectTerminated(underTest);
        }};
    }
}
