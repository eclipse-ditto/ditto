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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link PolicyEnforcerActor}.
 */
public final class PolicyEnforcerActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");
    private static ActorSystem actorSystem;

    @BeforeClass
    public static void init() {
        actorSystem = ActorSystem.create(PolicyEnforcerActorTest.class.getSimpleName(), CONFIG);
    }

    @AfterClass
    public static void shutdown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    /**
     * Checks that policy enforcer sends 1 request to policies shard region per timeout duration.
     * In the default configuration, 1 request is sent per policy ID every 5 seconds on timeout,
     * stressing policies service further.
     * <p>This test should fail! - therefore we expect the AssertionError!</p>
     */
    @Test(expected = AssertionError.class)
    public void cascadingFailure() throws InterruptedException {
        new TestKit(actorSystem) {{
            // set internal timeout to a low value to finish test quickly.
            final FiniteDuration internalTimeout = FiniteDuration.apply(1, TimeUnit.MILLISECONDS);
            final TestProbe thingsProbe = TestProbe.apply(actorSystem);
            final TestProbe pubSubProbe = TestProbe.apply(actorSystem);
            final TestProbe cacheProbe = TestProbe.apply(actorSystem);
            final String thingId = PolicyEnforcerActorTest.class.getSimpleName() + ".cascadingFailure:thingId";
            final ActorRef underTest = actorSystem.actorOf(
                    PolicyEnforcerActor.props(getRef(), thingsProbe.ref(),
                            pubSubProbe.ref(), cacheProbe.ref(), FiniteDuration.apply(1, TimeUnit.DAYS),
                            internalTimeout), thingId);

            // if policy shard region does not reply, PolicyEnforcerActor keeps firing RetrievePolicy messages.
            // send MessageCommand to trigger immediate reload.
            final int requireHowMany = 100;
            for (int i = 0; i < requireHowMany; ++i) {
                expectMsgClass(RetrievePolicy.class);
                final MessageHeaders messageHeaders =
                        MessageHeaders.newBuilder(MessageDirection.TO, thingId, "subject").build();
                underTest.tell(SendFeatureMessage.of(thingId, "featureId", Message.newBuilder(messageHeaders).build(),
                        DittoHeaders.empty()), getRef());
            }
        }};
    }

}
