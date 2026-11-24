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
import static org.eclipse.ditto.things.service.persistence.serializer.ThingPostgresEventAdapterGoldenTest.manifest;
import static org.eclipse.ditto.things.service.persistence.serializer.ThingPostgresEventAdapterGoldenTest.payload;
import static org.eclipse.ditto.things.service.persistence.serializer.ThingPostgresEventAdapterGoldenTest.rows;
import static org.eclipse.ditto.things.service.persistence.serializer.ThingPostgresEventAdapterGoldenTest.tags;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link WotValidationConfigPostgresEventAdapter} to
 * {@code golden/journal/wot-validation-config-postgres.json}: every event is written with the stored manifest, tags
 * and JSONB text, and every stored row replays to the recovered event. The events are those of
 * {@link WotValidationConfigMongoEventAdapterGoldenTest}.
 */
public final class WotValidationConfigPostgresEventAdapterGoldenTest {

    static final String FIXTURE = "wot-validation-config-postgres.json";

    @Nullable private static ExtendedActorSystem system;

    private final WotValidationConfigPostgresEventAdapter underTest =
            new WotValidationConfigPostgresEventAdapter(system);

    @BeforeClass
    public static void startActorSystem() {
        system = (ExtendedActorSystem) ActorSystem.create("WotValidationConfigPostgresEventAdapterGoldenTest",
                ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final List<JsonObject> rows = rows(FIXTURE);
        final List<WotValidationConfigEvent<?>> written =
                WotValidationConfigMongoEventAdapterGoldenTest.writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final JsonObject row = rows.get(i);
            final WotValidationConfigEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(manifest(row));
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags())).isEqualTo(tags(row));
            assertThat(tagged.payload()).isEqualTo(payload(row).toString());
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        final List<JsonObject> rows = rows(FIXTURE);
        final List<WotValidationConfigEvent<?>> recovered =
                WotValidationConfigMongoEventAdapterGoldenTest.recoveredEvents();
        assertThat(rows).hasSameSizeAs(recovered);

        for (int i = 0; i < rows.size(); i++) {
            final JsonObject row = rows.get(i);
            assertThat(CollectionConverters.asJava(
                    underTest.fromJournal(payload(row).toString(), manifest(row)).events()))
                    .containsExactly(recovered.get(i));
        }
    }

}
