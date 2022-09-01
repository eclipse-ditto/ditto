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
package org.eclipse.ditto.things.service.persistence.serializer;

import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.assertj.core.api.JUnitSoftAssertions;
import org.bson.BsonDocument;
import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.things.api.ThingSnapshotTaken;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotOffer;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link ThingMongoSnapshotAdapter}.
 */
public final class ThingMongoSnapshotAdapterTest {

    private static final String PERSISTENCE_ID = "thing:fajofj904q2";
    private static final SnapshotMetadata SNAPSHOT_METADATA = new SnapshotMetadata(PERSISTENCE_ID, 0, 0);

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

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
    public void toSnapshotStoreFromSnapshotStoreRoundTripV2ReturnsExpected() {
        final var thingV2 = TestConstants.Thing.THING_V2;
        toSnapshotStoreFromSnapshotStoreRoundTripReturnsExpected(thingV2);
        expectSnapshotPublished(thingV2);
    }

    private void toSnapshotStoreFromSnapshotStoreRoundTripReturnsExpected(final Thing thing) {
        final Object rawSnapshotEntity = underTest.toSnapshotStore(thing);

        softly.assertThat(rawSnapshotEntity).as("snapshot entity is BSON document").isInstanceOf(BsonDocument.class);

        final BsonDocument dbObject = (BsonDocument) rawSnapshotEntity;
        final Thing restoredThing = underTest.fromSnapshotStore(new SnapshotOffer(SNAPSHOT_METADATA, dbObject));

        softly.assertThat(restoredThing).as("restored Thing").isEqualTo(thing);
    }

    private void expectSnapshotPublished(final Thing thing) {
        final var thingJson = thing.toJson(thing.getImplementedSchemaVersion(), FieldType.regularOrSpecial());
        final var timestamp = Instant.now();
        final var expectedSnapshotTaken = ThingSnapshotTaken.newBuilder(TestConstants.Thing.THING_ID,
                TestConstants.Thing.REVISION_NUMBER,
                PersistenceLifecycle.ACTIVE,
                thingJson).build();

        final var receivedPublish = pubSubProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

        softly.assertThat(receivedPublish.topic())
                .as("topic")
                .isEqualTo(DistPubSubAccess.getGroupTopic(expectedSnapshotTaken.getPubSubTopic()));
        softly.assertThat(receivedPublish.message())
                .as("message")
                .isInstanceOfSatisfying(ThingSnapshotTaken.class, actualSnapshotTaken -> {
                    softly.assertThat((CharSequence) actualSnapshotTaken.getEntityId())
                            .as("entity ID")
                            .isEqualTo(TestConstants.Thing.THING_ID);
                    softly.assertThat(actualSnapshotTaken.getRevision())
                            .as("revision number")
                            .isEqualTo(TestConstants.Thing.REVISION_NUMBER);
                    softly.assertThat(actualSnapshotTaken.getTimestamp())
                            .as("timestamp")
                            .hasValueSatisfying(actualTimestamp -> softly.assertThat(actualTimestamp)
                                    .isCloseTo(timestamp, within(5, ChronoUnit.SECONDS)));
                    softly.assertThat(actualSnapshotTaken.getLifecycle())
                            .as("lifecycle")
                            .isEqualTo(PersistenceLifecycle.ACTIVE);
                    softly.assertThat(actualSnapshotTaken.getEntity()).as("entity").hasValue(thingJson);
                });
    }

}
