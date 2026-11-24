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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link ThingPostgresEventAdapter} to {@code golden/journal/thing-postgres.json}: every
 * event is written with the stored manifest, tags and JSONB text, and every stored row replays to the recovered event.
 * The events are those of {@link ThingMongoEventAdapterGoldenTest}.
 */
public final class ThingPostgresEventAdapterGoldenTest {

    static final String FIXTURE = "thing-postgres.json";

    @Nullable private static ExtendedActorSystem system;

    private final ThingPostgresEventAdapter underTest = new ThingPostgresEventAdapter(system);

    @BeforeClass
    public static void startActorSystem() {
        system = (ExtendedActorSystem) ActorSystem.create("ThingPostgresEventAdapterGoldenTest",
                ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final List<JsonObject> rows = rows(FIXTURE);
        final List<ThingEvent<?>> written = ThingMongoEventAdapterGoldenTest.writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final JsonObject row = rows.get(i);
            final ThingEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(manifest(row));
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags())).isEqualTo(tags(row));
            assertThat(tagged.payload()).isEqualTo(payload(row).toString());
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        final List<JsonObject> rows = rows(FIXTURE);
        final List<ThingEvent<?>> recovered = ThingMongoEventAdapterGoldenTest.recoveredEvents();
        assertThat(rows).hasSameSizeAs(recovered);

        for (int i = 0; i < rows.size(); i++) {
            final JsonObject row = rows.get(i);
            assertThat(CollectionConverters.asJava(
                    underTest.fromJournal(payload(row).toString(), manifest(row)).events()))
                    .containsExactly(recovered.get(i));
        }
    }

    static List<JsonObject> rows(final String fixture) throws IOException {
        return JsonObject.of(ThingMongoEventAdapterGoldenTest.readFixture(fixture))
                .getValueOrThrow(JsonFieldDefinition.ofJsonArray("rows"))
                .stream()
                .map(JsonValue::asObject)
                .toList();
    }

    static String manifest(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofString("manifest"));
    }

    static Set<String> tags(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofJsonArray("tags")).stream()
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
    }

    static JsonObject payload(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofJsonObject("payload"));
    }

}
