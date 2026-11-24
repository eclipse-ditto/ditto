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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.AfterClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Tests {@link ThingEventSerializer}: the inlined-policy stripping and the JSON round trip.
 */
public final class ThingEventSerializerTest {

    private static final ExtendedActorSystem SYSTEM =
            (ExtendedActorSystem) ActorSystem.create("ThingEventSerializerTest", ConfigFactory.load("test"));

    private static final ThingId THING_ID = ThingId.of("serializer:thing");
    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");
    private static final JsonObject INLINED_POLICY = JsonObject.newBuilder()
            .set("policyId", THING_ID.toString())
            .set("entries", JsonObject.empty())
            .build();

    private final ThingEventSerializer underTest = new ThingEventSerializer(SYSTEM);

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(SYSTEM);
    }

    @Test
    public void toJournalJsonStripsTheInlinedPolicyFromTheThingPayload() {
        final ThingCreated event = mock(ThingCreated.class);
        final JsonObject eventJson = JsonObject.newBuilder()
                .set("type", ThingCreated.TYPE)
                .set("thing", JsonObject.newBuilder()
                        .set("thingId", THING_ID.toString())
                        .set("_policy", INLINED_POLICY)
                        .build())
                .build();
        when(event.getImplementedSchemaVersion()).thenReturn(JsonSchemaVersion.V_2);
        when(event.toJson(eq(JsonSchemaVersion.V_2), any(Predicate.class))).thenReturn(eventJson);
        when(event.getDittoHeaders()).thenReturn(DittoHeaders.empty());

        final JsonObject journalJson = underTest.toJournalJson(event);

        assertThat(journalJson.getValue(JsonPointer.of("/thing/_policy"))).isEmpty();
        assertThat(journalJson.getValue(JsonPointer.of("/thing/thingId")))
                .contains(JsonValue.of(THING_ID.toString()));
        assertThat(journalJson.contains(EventSerializer.HISTORICAL_EVENT_HEADERS.getPointer())).isTrue();
    }

    @Test
    public void toJournalJsonStripsTheInlinedPolicyFromARootThingMergedValue() {
        final JsonObject value = JsonObject.newBuilder()
                .set("thingId", THING_ID.toString())
                .set("_policy", INLINED_POLICY)
                .build();
        final ThingMerged event =
                ThingMerged.of(THING_ID, JsonPointer.empty(), value, 2L, TIMESTAMP, DittoHeaders.empty(), null);

        final JsonObject journalJson = underTest.toJournalJson(event);

        assertThat(journalJson.getValue(JsonPointer.of("/value/_policy"))).isEmpty();
        assertThat(journalJson.getValue(JsonPointer.of("/value/thingId")))
                .contains(JsonValue.of(THING_ID.toString()));
    }

    @Test
    public void toJournalJsonKeepsTheValueOfANonRootThingMerged() {
        final JsonObject value = JsonObject.newBuilder().set("_policy", INLINED_POLICY).build();
        final ThingMerged event = ThingMerged.of(THING_ID, JsonPointer.of("/attributes"), value, 2L, TIMESTAMP,
                DittoHeaders.empty(), null);

        final JsonObject journalJson = underTest.toJournalJson(event);

        assertThat(journalJson.getValue(JsonPointer.of("/value"))).contains(value);
    }

    @Test
    public void fromJournalJsonRecoversTheEventWrittenByToJournalJson() {
        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setModified(TIMESTAMP)
                .setCreated(TIMESTAMP)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(1)
                .setPolicyId(PolicyId.of(THING_ID))
                .setAttributes(Attributes.newBuilder().set("hello", "serializer").build())
                .build();
        // recovery resets the event revision to the default; the persistence layer supplies the real one
        final ThingCreated event = ThingCreated.of(thing, 0L, TIMESTAMP, DittoHeaders.empty(), null);

        final List<Object> recovered = CollectionConverters.asJava(
                underTest.fromJournalJson(underTest.toJournalJson(event), event.getType()).events());

        assertThat(recovered).containsExactly(event);
    }

}
