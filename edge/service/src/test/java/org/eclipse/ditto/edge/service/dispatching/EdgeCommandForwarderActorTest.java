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
package org.eclipse.ditto.edge.service.dispatching;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link EdgeCommandForwarderActor}.
 */
public final class EdgeCommandForwarderActorTest {

    public static final PolicyId POLICY_ID = PolicyId.of("foo:bar");
    public static final ThingId THING_ID = ThingId.of("foo:bar");
    @Nullable private static ActorSystem actorSystem;

    @BeforeClass
    public static void init() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void ensureCommandOrderIsMaintainedForSlowSignalTransformations() {
        assert actorSystem != null;
        new TestKit(actorSystem) {{
            final ShardRegions shardRegionsMock = Mockito.mock(ShardRegions.class);
            final TestProbe thingsProbe = new TestProbe(actorSystem);
            Mockito.when(shardRegionsMock.things()).thenReturn(thingsProbe.ref());

            final Props props = EdgeCommandForwarderActor.props(getRef(), shardRegionsMock);
            final ActorRef underTest = actorSystem.actorOf(props);

            final CreateThing createThing = CreateThing.of(Thing.newBuilder()
                            .setId(THING_ID)
                            .setPolicyId(POLICY_ID)
                            .build(),
                    null,
                    DittoHeaders.newBuilder().correlationId("cid-1-create").build()
            );
            final ModifyAttribute modifyAttribute = ModifyAttribute.of(THING_ID,
                    JsonPointer.of("foo"),
                    JsonValue.of(42),
                    DittoHeaders.newBuilder().correlationId("cid-2-modify").build()
            );

            underTest.tell(createThing, getRef());
            underTest.tell(modifyAttribute, getRef());

            thingsProbe.expectMsg(createThing);
            thingsProbe.expectMsg(modifyAttribute);
        }};
    }
}
