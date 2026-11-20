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
package org.eclipse.ditto.policies.service.persistence.serializer;

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
import org.eclipse.ditto.internal.utils.persistence.postgres.adapters.PolicyPostgresEventAdapter;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link PolicyPostgresEventAdapter} to {@code golden/journal/policy-postgres.json}: every
 * event is written with the stored manifest, tags and JSONB text, and every stored row replays to the recovered event.
 * The events are those of {@link PolicyMongoEventAdapterGoldenTest}; the legacy row is the MongoDB one, fed as JSONB
 * text.
 */
public final class PolicyPostgresEventAdapterGoldenTest {

    static final String FIXTURE = "policy-postgres.json";

    @Nullable private static ExtendedActorSystem system;

    private final PolicyPostgresEventAdapter underTest = new PolicyPostgresEventAdapter(system);

    @BeforeClass
    public static void startActorSystem() {
        system = (ExtendedActorSystem) ActorSystem.create("PolicyPostgresEventAdapterGoldenTest",
                ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final List<JsonObject> rows = rows();
        final List<PolicyEvent<?>> written = PolicyMongoEventAdapterGoldenTest.writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final JsonObject row = rows.get(i);
            final PolicyEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(manifest(row));
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags())).isEqualTo(tags(row));
            assertThat(tagged.payload()).isEqualTo(payload(row).toString());
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        final List<JsonObject> rows = rows();
        final List<PolicyEvent<?>> recovered = PolicyMongoEventAdapterGoldenTest.recoveredEvents();
        assertThat(rows).hasSameSizeAs(recovered);

        for (int i = 0; i < rows.size(); i++) {
            final JsonObject row = rows.get(i);
            assertThat(CollectionConverters.asJava(
                    underTest.fromJournal(payload(row).toString(), manifest(row)).events()))
                    .containsExactly(recovered.get(i));
        }
    }

    @Test
    public void replaysTheLegacyRowToNoEvent() throws IOException {
        final String legacy =
                PolicyMongoEventAdapterGoldenTest.readFixture(PolicyMongoEventAdapterGoldenTest.LEGACY_FIXTURE);
        final PolicyMongoEventAdapterGoldenTest.DiscardLogCapture logCapture =
                new PolicyMongoEventAdapterGoldenTest.DiscardLogCapture(PolicyEventSerializer.class.getName());
        logCapture.start();
        try {
            assertThat(CollectionConverters.asJava(
                    underTest.fromJournal(legacy, PolicyMongoEventAdapterGoldenTest.LEGACY_MANIFEST).events()))
                    .isEmpty();
            logCapture.assertSingleDiscardLineFor(PolicyMongoEventAdapterGoldenTest.LEGACY_MANIFEST);
        } finally {
            logCapture.stop();
        }
    }

    private static List<JsonObject> rows() throws IOException {
        return JsonObject.of(PolicyMongoEventAdapterGoldenTest.readFixture(FIXTURE))
                .getValueOrThrow(JsonFieldDefinition.ofJsonArray("rows"))
                .stream()
                .map(JsonValue::asObject)
                .toList();
    }

    private static String manifest(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofString("manifest"));
    }

    private static Set<String> tags(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofJsonArray("tags")).stream()
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
    }

    private static JsonObject payload(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofJsonObject("payload"));
    }

}
