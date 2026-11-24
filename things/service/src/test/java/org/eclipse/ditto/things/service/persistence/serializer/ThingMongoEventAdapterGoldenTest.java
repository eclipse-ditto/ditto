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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link ThingMongoEventAdapter} to {@code golden/journal/thing-mongo.json}: every event is
 * written with the stored manifest, tags and BSON payload, and every stored row replays to the recovered event. The
 * written thing events carry an inlined policy, which the stored rows do not contain.
 */
public final class ThingMongoEventAdapterGoldenTest {

    static final String FIXTURE = "thing-mongo.json";

    private static final ThingId THING_ID = ThingId.of("golden:thing");
    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("golden-thing").build();
    private static final JsonObject INLINED_POLICY = JsonObject.newBuilder()
            .set("policyId", THING_ID.toString())
            .set("entries", JsonObject.newBuilder()
                    .set("DEFAULT", JsonObject.newBuilder()
                            .set("subjects", JsonObject.newBuilder()
                                    .set("google:golden-subject", JsonObject.newBuilder()
                                            .set("type", "golden")
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();

    @Nullable private static ExtendedActorSystem system;

    private final ThingMongoEventAdapter underTest = new ThingMongoEventAdapter(system);

    @BeforeClass
    public static void startActorSystem() {
        system = (ExtendedActorSystem) ActorSystem.create("ThingMongoEventAdapterGoldenTest",
                ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final List<BsonDocument> rows = rows();
        final List<ThingEvent<?>> written = writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final BsonDocument row = rows.get(i);
            final ThingEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(row.getString("manifest").getValue());
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags())).isEqualTo(tags(row));
            assertThat(tagged.payload()).isEqualTo(row.getDocument("payload"));
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        final List<BsonDocument> rows = rows();
        final List<ThingEvent<?>> recovered = recoveredEvents();
        assertThat(rows).hasSameSizeAs(recovered);

        for (int i = 0; i < rows.size(); i++) {
            final BsonDocument row = rows.get(i);
            assertThat(CollectionConverters.asJava(
                    underTest.fromJournal(row.getDocument("payload"), row.getString("manifest").getValue())
                            .events()))
                    .containsExactly(recovered.get(i));
        }
    }

    /**
     * @return the events written to the fixture, in row order; the created thing and the root merge value carry an
     * inlined policy.
     */
    static List<ThingEvent<?>> writtenEvents() {
        final ThingCreated created = ThingCreated.of(thing("golden"), 1L, TIMESTAMP, HEADERS, null);
        final JsonObject createdJsonWithPolicy = created.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial())
                .setValue(JsonPointer.of("/thing/_policy"), INLINED_POLICY);
        final ThingCreated createdWithPolicy = spy(created);
        doReturn(createdJsonWithPolicy).when(createdWithPolicy)
                .toJson(any(JsonSchemaVersion.class), any(Predicate.class));

        return List.of(createdWithPolicy,
                ThingMerged.of(THING_ID, JsonPointer.empty(), mergeValue().setValue("_policy", INLINED_POLICY), 2L,
                        TIMESTAMP, HEADERS, null),
                ThingModified.of(thing("modified"), 3L, TIMESTAMP, HEADERS, null),
                ThingDeleted.of(THING_ID, 4L, TIMESTAMP, HEADERS, null));
    }

    /**
     * @return the events the fixture rows replay to, in row order: recovery resets the revision to the default and
     * restores only the persisted historical headers; the inlined policy is not stored.
     */
    static List<ThingEvent<?>> recoveredEvents() {
        return List.of(ThingCreated.of(thing("golden"), 0L, TIMESTAMP, HEADERS, null),
                ThingMerged.of(THING_ID, JsonPointer.empty(), mergeValue(), 0L, TIMESTAMP, HEADERS, null),
                ThingModified.of(thing("modified"), 0L, TIMESTAMP, HEADERS, null),
                ThingDeleted.of(THING_ID, 0L, TIMESTAMP, HEADERS, null));
    }

    static String readFixture(final String name) throws IOException {
        try (final InputStream in = Objects.requireNonNull(
                ThingMongoEventAdapterGoldenTest.class.getResourceAsStream("/golden/journal/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<BsonDocument> rows(final String fixture) throws IOException {
        return BsonDocument.parse(readFixture(fixture)).getArray("rows").stream()
                .map(BsonValue::asDocument)
                .toList();
    }

    static Set<String> tags(final BsonDocument row) {
        return row.getArray("tags").stream()
                .map(BsonValue::asString)
                .map(BsonString::getValue)
                .collect(Collectors.toSet());
    }

    private static List<BsonDocument> rows() throws IOException {
        return rows(FIXTURE);
    }

    private static Thing thing(final String hello) {
        return Thing.newBuilder()
                .setId(THING_ID)
                .setModified(TIMESTAMP)
                .setCreated(TIMESTAMP)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(1)
                .setPolicyId(PolicyId.of(THING_ID))
                .setAttributes(Attributes.newBuilder().set("hello", hello).build())
                .build();
    }

    private static JsonObject mergeValue() {
        return JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder().set("hello", "merged").build())
                .build();
    }

}
