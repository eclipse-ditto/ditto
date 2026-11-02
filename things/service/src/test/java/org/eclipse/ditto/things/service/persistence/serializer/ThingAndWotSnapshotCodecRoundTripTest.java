/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.NeutralSnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.SnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoSnapshotCodec;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotOffer;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Round-trip guard for the two entity types persisted inside the Things JVM: a {@link Thing} (via the
 * {@link ThingMongoSnapshotAdapter} serializer) AND a {@link WotValidationConfig} (via its own
 * {@link WotValidationConfigSnapshotSerializer}) each survive a full {@code domain -> store payload -> domain}
 * round-trip through their per-entity serializer composed with the active backend's {@link SnapshotCodec} (here the
 * Mongo BSON codec, the Things-JVM default).
 * <p>
 * The WoT case is the real reproduction of the live {@code ClassCastException}: the things-JVM-wide {@code
 * snapshot-adapter} is the Thing serializer, so before {@code WotValidationConfigPersistenceActor} got its own
 * serializer hook, a WoT snapshot went through the Thing serializer and blew up. This test pins that the WoT serializer
 * round-trips a {@link WotValidationConfig} (active and deleted) AND that the old path (Thing serializer fed a
 * {@link WotValidationConfig}) still throws — documenting exactly what was broken.
 */
public final class ThingAndWotSnapshotCodecRoundTripTest {

    private static final SnapshotMetadata SNAPSHOT_METADATA = new SnapshotMetadata("pid", 7L, 0L);

    private ActorSystem system;
    private TestProbe pubSubProbe;

    @Before
    public void setUp() {
        system = ActorSystem.create();
        pubSubProbe = TestProbe.apply(system);
    }

    @After
    public void cleanUp() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void thingSnapshotRoundTripsViaThingSerializerAndMongoCodec() {
        final SnapshotCodec codec = MongoSnapshotCodec.INSTANCE;
        final SnapshotAdapter<Thing> adapter = new NeutralSnapshotAdapter<>(
                new ThingMongoSnapshotAdapter(pubSubProbe.ref(), ConfigFactory.parseMap(
                        Map.of(ThingMongoSnapshotAdapter.THING_SNAPSHOT_TAKEN_EVENT_PUBLISHING_ENABLED, false))),
                codec);

        final Thing thing = TestConstants.Thing.THING_V2;

        // Full neutral-adapter round trip (serializer + codec): domain -> store payload -> domain.
        final Object stored = adapter.toSnapshotStore(thing);
        final Thing restored = adapter.fromSnapshotStore(new SnapshotOffer(SNAPSHOT_METADATA, stored));
        assertThat(restored).as("Thing recovered identically via NeutralSnapshotAdapter").isEqualTo(thing);
    }

    @Test
    public void wotValidationConfigSnapshotRoundTripsViaWotSerializerAndMongoCodec() {
        final SnapshotCodec codec = MongoSnapshotCodec.INSTANCE;
        final SnapshotAdapter<WotValidationConfig> adapter = new NeutralSnapshotAdapter<>(
                new WotValidationConfigSnapshotSerializer(), codec);

        final WotValidationConfig config = activeConfig();

        // The full path that used to ClassCastException: WotValidationConfig -> WoT serializer -> codec -> recover.
        final Object stored = adapter.toSnapshotStore(config);
        final WotValidationConfig restored = adapter.fromSnapshotStore(new SnapshotOffer(SNAPSHOT_METADATA, stored));
        assertThat(restored).as("WotValidationConfig recovered identically via its own serializer").isEqualTo(config);
    }

    @Test
    public void deletedWotValidationConfigSnapshotRecoversAsNull() {
        final SnapshotCodec codec = MongoSnapshotCodec.INSTANCE;
        final SnapshotAdapter<WotValidationConfig> adapter = new NeutralSnapshotAdapter<>(
                new WotValidationConfigSnapshotSerializer(), codec);

        final Instant timestamp = Instant.parse("2026-06-18T12:00:00Z");
        final WotValidationConfig deleted = WotValidationConfig.of(
                WotValidationConfigId.of("ns:test-id"), true, false, null, null, Collections.emptyList(),
                WotValidationConfigRevision.of(2L), timestamp, timestamp, true, null);

        final Object stored = adapter.toSnapshotStore(deleted);
        final WotValidationConfig restored = adapter.fromSnapshotStore(new SnapshotOffer(SNAPSHOT_METADATA, stored));
        assertThat(restored).as("a deleted WoT snapshot recovers as null, like a deleted Thing").isNull();
    }

    @Test
    public void thingSerializerFedAWotConfigStillThrows_documentingTheOriginalBug() {
        // Before the fix, WotValidationConfigPersistenceActor inherited the Thing serializer and a WoT snapshot hit
        // this exact ClassCastException. This pins WHY the WoT serializer is needed.
        final SnapshotCodec codec = MongoSnapshotCodec.INSTANCE;
        @SuppressWarnings({"unchecked", "rawtypes"})
        final SnapshotAdapter<WotValidationConfig> wronglyTypedThingAdapter = new NeutralSnapshotAdapter(
                new ThingMongoSnapshotAdapter(pubSubProbe.ref(), ConfigFactory.parseMap(
                        Map.of(ThingMongoSnapshotAdapter.THING_SNAPSHOT_TAKEN_EVENT_PUBLISHING_ENABLED, false))),
                codec);

        assertThatThrownBy(() -> wronglyTypedThingAdapter.toSnapshotStore(activeConfig()))
                .isInstanceOf(ClassCastException.class);
    }

    private static WotValidationConfig activeConfig() {
        // A single fixed timestamp for created/modified: WotValidationConfig.fromJson re-parses the JSON body, so the
        // fixture must be reconstructible from its own JSON (Instant.now() twice would differ by nanos and not survive).
        final Instant timestamp = Instant.parse("2026-06-18T12:00:00Z");
        return WotValidationConfig.of(
                WotValidationConfigId.of("ns:test-id"), true, false, null, null, Collections.emptyList(),
                WotValidationConfigRevision.of(1L), timestamp, timestamp, false, null);
    }
}
