/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public abstract class AbstractPublisherActorTest<T> {

    private static final Config CONFIG = ConfigFactory.load("test");
    protected static ActorSystem actorSystem;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testPublishMessage() throws Exception {

        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal outboundSignal = mock(OutboundSignal.class);
            final Signal source = mock(Signal.class);
            when(source.getId()).thenReturn(TestConstants.Things.THING_ID);
            when(outboundSignal.getSource()).thenReturn(source);
            final Target target =
                    ConnectivityModelFactory.newTargetBuilder()
                            .address(getOutboundAddress())
                            .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                            .headerMapping(TestConstants.HEADER_MAPPING)
                            .topics(Topic.TWIN_EVENTS)
                            .build();
            when(outboundSignal.getTargets()).thenReturn(Collections.singleton(decorateTarget(target)));


            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().putHeader("device_id", "ditto:thing").build();
            final ExternalMessage externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(dittoHeaders).withText("payload").build();
            final OutboundSignal.WithExternalMessage mappedOutboundSignal =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, externalMessage);

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = actorSystem.actorOf(props);

            publisherCreated(publisherActor);

            publisherActor.tell(mappedOutboundSignal, getRef());

            verifyPublishedMessage();
        }};

    }

    protected abstract String getOutboundAddress();
    protected abstract void setupMocks(final TestProbe probe) throws Exception;
    protected abstract Props getPublisherActorProps();
    protected abstract void publisherCreated(ActorRef publisherActor);

    protected abstract Target decorateTarget(Target target);

    protected abstract void verifyPublishedMessage() throws Exception;

}
