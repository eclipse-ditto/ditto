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
package org.eclipse.ditto.internal.utils.persistentactors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.persistentactors.config.DefaultPingConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link PersistencePingActor}.
 */
public final class PersistencePingActorTest {

    public static final Config CONFIG = ConfigFactory.load("persistence-ping-test");

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Test
    public void conversionBetweenCorrelationIdAndPersistenceIdIsOneToOne() {
        final PolicyId id1 = PolicyId.of("foo:random-ID-jbxlkeimx");
        final PolicyId id2 = PolicyId.of("foo:differentId");
        final Optional<String> outputId1 = PersistencePingActor.toPersistenceId(
                PersistencePingActor.toCorrelationId(id1));
        final Optional<String> outputId2 = PersistencePingActor.toPersistenceId(
                PersistencePingActor.toCorrelationId(id2));
        assertThat(outputId1).contains(id1.toString());
        assertThat(outputId2).contains(id2.toString());
        assertThat(outputId1).isNotEqualTo(outputId2);
    }

    @Test
    public void pingPersistenceActors() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);

            final PingConfig pingConfig =
                    DefaultPingConfig.of(actorSystem.settings().config().getConfig("ditto.test"));
            final PolicyId persistenceId1 = PolicyId.of("some:pid-1");
            final PolicyId persistenceId2 = PolicyId.of("some:pid-2");
            final PolicyId persistenceId3 = PolicyId.of("some:pid-3");
            final Props props = PersistencePingActor.propsForTests(probe.ref(), pingConfig,
                    () -> Source.from(Arrays.asList(
                            persistenceId1.getEntityType() + ":" + persistenceId1,
                            persistenceId1.getEntityType() + ":" + persistenceId2,
                            persistenceId1.getEntityType() + ":" + persistenceId3)));

            actorSystem.actorOf(props);

            final PingCommand msg1 = probe.expectMsgClass(PingCommand.class);
            assertThat((CharSequence) msg1.getEntityId()).isEqualTo(persistenceId1);
            assertThat(msg1.getCorrelationId()).contains(PersistencePingActor.toCorrelationId(persistenceId1));
            final PingCommand msg2 = probe.expectMsgClass(PingCommand.class);
            assertThat((CharSequence) msg2.getEntityId()).isEqualTo(persistenceId2);
            assertThat(msg2.getCorrelationId()).contains(PersistencePingActor.toCorrelationId(persistenceId2));
            final PingCommand msg3 = probe.expectMsgClass(PingCommand.class);
            assertThat((CharSequence) msg3.getEntityId()).isEqualTo(persistenceId3);
            assertThat(msg3.getCorrelationId()).contains(PersistencePingActor.toCorrelationId(persistenceId3));
        }};
    }

    @Test
    public void testPersistenceActorIsNotPingedTwice() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);

            final PingConfig pingConfig =
                    DefaultPingConfig.of(actorSystem.settings().config().getConfig("ditto.test"));
            final String persistenceId1 = "some:pid-1";
            final String persistenceId2 = "some:pid-2";
            final String persistenceId3 = "some:pid-3";
            final Props props = PersistencePingActor.propsForTests(probe.ref(), pingConfig,
                    () -> Source.from(Arrays.asList(persistenceId1, persistenceId2, persistenceId3)));

            final ActorRef reconnectActor = actorSystem.actorOf(props);
            reconnectActor.tell(PersistencePingActor.InternalMessages.START_PINGING, getRef());

            probe.expectMsgClass(PingCommand.class);
            probe.expectMsgClass(PingCommand.class);
            probe.expectMsgClass(PingCommand.class);
            probe.expectNoMessage();
        }};
    }

}
