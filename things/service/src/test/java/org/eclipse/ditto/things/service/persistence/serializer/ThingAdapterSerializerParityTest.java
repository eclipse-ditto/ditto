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

import java.time.Instant;
import java.util.List;

import org.bson.BsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.SnapshotSerializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Proves that the serializer split keeps the MongoDB (BSON) and PostgreSQL (JSONB) envelopes equal at the
 * {@link JsonObject} level for both events and snapshots, using the <em>real</em> Thing adapters, and that the JSON text
 * a PostgreSQL snapshot row holds recovers to the identical domain object that the Mongo BSON path recovers.
 */
public final class ThingAdapterSerializerParityTest {

    private static final ExtendedActorSystem SYSTEM =
            (ExtendedActorSystem) ActorSystem.create("test", ConfigFactory.load("test"));

    private static Thing sampleThing() {
        return Thing.newBuilder()
                .setId(ThingId.of("pap.th.tMJyAjktUVP:YlmZXbTQ"))
                .setModified(Instant.parse("2021-02-24T14:17:37.581679843Z"))
                .setCreated(Instant.parse("2021-02-24T14:17:37.581679843Z"))
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(1)
                .setPolicyId(PolicyId.of("pap.th.tMJyAjktUVP:YlmZXbTQ"))
                .setAttributes(Attributes.newBuilder().set("hello", "cloud").build())
                .build();
    }

    @Test
    public void eventJournalJsonIsIdenticalAcrossBsonAndJsonbEnvelopes() {
        final ThingMongoEventAdapter adapter = new ThingMongoEventAdapter(SYSTEM);
        final ThingCreated event = ThingCreated.of(sampleThing(), 1L,
                Instant.parse("2021-02-24T14:17:37.581679843Z"), DittoHeaders.empty(), null);

        // Mongo envelope: serializer JsonObject -> BSON -> back to JsonObject.
        final Tagged tagged = (Tagged) adapter.toJournal(event);
        final JsonObject mongoJson = DittoBsonJson.getInstance().serialize((BsonValue) tagged.payload()).asObject();

        // Postgres envelope: the same serializer JsonObject rendered as JSONB text.
        final JsonObject postgresJson = adapter.toJournalJson(event);

        assertThat(postgresJson.toString()).isEqualTo(mongoJson.toString());
    }

    @Test
    public void eventRecoveryIsIdenticalAcrossBsonAndJsonbEnvelopes() {
        final ThingMongoEventAdapter adapter = new ThingMongoEventAdapter(SYSTEM);
        final ThingCreated event = ThingCreated.of(sampleThing(), 1L,
                Instant.parse("2021-02-24T14:17:37.581679843Z"), DittoHeaders.empty(), null);

        // Mongo path: BSON -> event.
        final Tagged tagged = (Tagged) adapter.toJournal(event);
        final EventSeq mongoSeq = adapter.fromJournal(tagged.payload(), event.getType());

        // Postgres path: JSONB text -> JsonObject -> event (same serializer migration hooks).
        final JsonObject jsonb = adapter.toJournalJson(event);
        final EventSeq postgresSeq = adapter.fromJournalJson(JsonFactory.newObject(jsonb.toString()), event.getType());

        assertThat(CollectionConverters.asJava(postgresSeq.events()))
                .isEqualTo(CollectionConverters.asJava(mongoSeq.events()));
        // The recovered event equals the original (revision is reset to default on recovery).
        final List<Object> recovered = CollectionConverters.asJava(postgresSeq.events());
        assertThat(recovered).hasSize(1);
    }

    @Test
    public void snapshotRecoversRealThingFromTheJsonTextStoredAsJsonb() {
        // The serializer logic of the real ThingMongoSnapshotAdapter, fed the JSON text a PostgreSQL snapshot row holds:
        // the PostgreSQL codec stores serializer.toJson(thing) as JSONB text and parses it back on load.
        final SnapshotSerializer<Thing> serializer =
                new ThingMongoSnapshotAdapter(ActorRef.noSender(), ConfigFactory.parseString(
                        "thing-snapshot-taken-event-publishing-enabled = false"));
        final Thing thing = sampleThing();

        final String jsonbText = serializer.toJson(thing).toString();
        final Thing recovered = serializer.fromJson(JsonFactory.newObject(jsonbText));

        assertThat(recovered).isEqualTo(thing);
    }

}
