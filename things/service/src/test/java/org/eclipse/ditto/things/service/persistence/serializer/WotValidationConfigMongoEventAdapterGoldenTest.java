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
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigCreated;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigDeleted;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigModified;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link WotValidationConfigMongoEventAdapter} to
 * {@code golden/journal/wot-validation-config-mongo.json}: every event is written with the stored manifest, tags and
 * BSON payload, and every stored row replays to the recovered event. The written events carry no journal tags in
 * their headers, so the stored tags are the ones the adapter derives.
 */
public final class WotValidationConfigMongoEventAdapterGoldenTest {

    static final String FIXTURE = "wot-validation-config-mongo.json";

    private static final WotValidationConfigId CONFIG_ID = WotValidationConfigId.of("golden:wot");
    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("golden-wot").build();

    @Nullable private static ExtendedActorSystem system;

    private final WotValidationConfigMongoEventAdapter underTest = new WotValidationConfigMongoEventAdapter(system);

    @BeforeClass
    public static void startActorSystem() {
        system = (ExtendedActorSystem) ActorSystem.create("WotValidationConfigMongoEventAdapterGoldenTest",
                ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final List<BsonDocument> rows = ThingMongoEventAdapterGoldenTest.rows(FIXTURE);
        final List<WotValidationConfigEvent<?>> written = writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final BsonDocument row = rows.get(i);
            final WotValidationConfigEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(row.getString("manifest").getValue());
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags()))
                    .isEqualTo(ThingMongoEventAdapterGoldenTest.tags(row));
            assertThat(tagged.payload()).isEqualTo(row.getDocument("payload"));
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        final List<BsonDocument> rows = ThingMongoEventAdapterGoldenTest.rows(FIXTURE);
        final List<WotValidationConfigEvent<?>> recovered = recoveredEvents();
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
     * @return the events written to the fixture, in row order.
     */
    static List<WotValidationConfigEvent<?>> writtenEvents() {
        return List.of(WotValidationConfigCreated.of(CONFIG_ID, config(true, 1L), 1L, TIMESTAMP, HEADERS, null),
                WotValidationConfigModified.of(CONFIG_ID, config(false, 1L), 2L, TIMESTAMP, HEADERS, null),
                WotValidationConfigDeleted.of(CONFIG_ID, 3L, TIMESTAMP, HEADERS, null));
    }

    /**
     * @return the events the fixture rows replay to, in row order: recovery resets the revision to the default and
     * restores only the persisted historical headers. The stored {@code "_metadata": null} parses to null metadata for
     * the created and the modified event and to no metadata for the deleted one; a recovered modification carries the
     * default revision in its config too.
     */
    static List<WotValidationConfigEvent<?>> recoveredEvents() {
        return List.of(WotValidationConfigCreated.of(CONFIG_ID, config(true, 1L), 0L, TIMESTAMP, HEADERS,
                        MetadataModelFactory.nullMetadata()),
                WotValidationConfigModified.of(CONFIG_ID, config(false, 0L), 0L, TIMESTAMP, HEADERS,
                        MetadataModelFactory.nullMetadata()),
                WotValidationConfigDeleted.of(CONFIG_ID, 0L, TIMESTAMP, HEADERS, null));
    }

    private static WotValidationConfig config(final boolean enabled, final long revision) {
        return WotValidationConfig.of(CONFIG_ID, enabled, false,
                ThingValidationConfig.of(ThingValidationEnforceConfig.of(true, true, true, true, true),
                        ThingValidationForbidConfig.of(false, true, false, true)),
                FeatureValidationConfig.of(
                        FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true),
                        FeatureValidationForbidConfig.of(false, true, false, true, false, true)),
                Collections.emptyList(), WotValidationConfigRevision.of(revision), TIMESTAMP, TIMESTAMP, false, null);
    }

}
