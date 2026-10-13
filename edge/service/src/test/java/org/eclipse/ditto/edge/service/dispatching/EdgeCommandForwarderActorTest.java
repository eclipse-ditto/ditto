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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link EdgeCommandForwarderActor}.
 */
public final class EdgeCommandForwarderActorTest {

    public static final PolicyId POLICY_ID = PolicyId.of("foo:bar");
    public static final ThingId THING_ID = ThingId.of("foo:bar");
    @Nullable private static ActorSystem actorSystem;

    @BeforeClass
    public static void init() {
        actorSystem = ActorSystem.create("PekkoTestSystem", ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void retrieveTimeseriesIsForwardedToTimeseriesShardRegion() {
        // The per-Thing TimeseriesIngestActor handles both ingest and query, so the forwarder
        // routes RetrieveTimeseries via askWithRetryCommandForwarder against the timeseries
        // shard region — same shape as forwardToThings / forwardToPolicies.
        assert actorSystem != null;
        new TestKit(actorSystem) {{
            final ShardRegions shardRegionsMock = Mockito.mock(ShardRegions.class);
            final TestProbe timeseriesProbe = new TestProbe(actorSystem);
            Mockito.when(shardRegionsMock.timeseries()).thenReturn(timeseriesProbe.ref());

            final Props props = EdgeCommandForwarderActor.props(getRef(), shardRegionsMock);
            final ActorRef underTest = actorSystem.actorOf(props);

            final java.time.Instant from = java.time.Instant.parse("2026-01-14T00:00:00Z");
            final java.time.Instant to = java.time.Instant.parse("2026-01-15T00:00:00Z");
            final org.eclipse.ditto.timeseries.model.TimeseriesQuery query =
                    org.eclipse.ditto.timeseries.model.TimeseriesQuery.of(THING_ID,
                            java.util.Collections.singletonList(JsonPointer.of(
                                    "/features/env/properties/temperature")),
                            from, to);
            final org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries cmd =
                    org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries.of(
                            query,
                            DittoHeaders.newBuilder().correlationId("ts-fwd-test").build());

            underTest.tell(cmd, getRef());

            timeseriesProbe.expectMsg(cmd);
        }};
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
