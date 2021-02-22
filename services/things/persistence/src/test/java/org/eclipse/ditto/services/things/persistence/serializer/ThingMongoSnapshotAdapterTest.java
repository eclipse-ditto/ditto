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
package org.eclipse.ditto.services.things.persistence.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.BsonDocument;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.DittoThingSnapshotTaken;
import org.eclipse.ditto.services.models.things.ThingSnapshotTaken;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotOffer;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link org.eclipse.ditto.services.things.persistence.serializer.ThingMongoSnapshotAdapter}.
 */
public final class ThingMongoSnapshotAdapterTest {

    private static final String PERSISTENCE_ID = "thing:fajofj904q2";
    private static final SnapshotMetadata SNAPSHOT_METADATA = new SnapshotMetadata(PERSISTENCE_ID, 0, 0);

    private ActorSystem system;
    private TestProbe pubSubProbe;
    private ThingMongoSnapshotAdapter underTest = null;

    @Before
    public void setUp() {
        system = ActorSystem.create();
        pubSubProbe = TestProbe.apply(system);
        underTest = new ThingMongoSnapshotAdapter(pubSubProbe.ref());
    }

    @After
    public void cleanUp() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void toSnapshotStoreFromSnapshotStoreRoundtripV2ReturnsExpected() {
        toSnapshotStoreFromSnapshotStoreRoundtripReturnsExpected(TestConstants.Thing.THING_V2);
        expectSnapshotPublished();
    }

    private void expectSnapshotPublished() {
        pubSubProbe.expectMsg(DistPubSubAccess.publishViaGroup(
                ThingSnapshotTaken.PUBSUB_TOPIC,
                DittoThingSnapshotTaken.of(TestConstants.Thing.THING_ID)));
    }

    private void toSnapshotStoreFromSnapshotStoreRoundtripReturnsExpected(final Thing thing) {
        final Object rawSnapshotEntity = underTest.toSnapshotStore(thing);
        assertThat(rawSnapshotEntity).isInstanceOf(BsonDocument.class);
        final BsonDocument dbObject = (BsonDocument) rawSnapshotEntity;
        final Thing restoredThing = underTest.fromSnapshotStore(new SnapshotOffer(SNAPSHOT_METADATA, dbObject));
        assertThat(restoredThing).isEqualTo(thing);
    }

}
