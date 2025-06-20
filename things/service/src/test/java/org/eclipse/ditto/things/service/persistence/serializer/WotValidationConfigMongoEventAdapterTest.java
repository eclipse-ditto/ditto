/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
import java.util.Collections;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigCreated;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigDeleted;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigModified;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class WotValidationConfigMongoEventAdapterTest {

    private ExtendedActorSystem actorSystem;
    private WotValidationConfigMongoEventAdapter underTest;
    private WotValidationConfigId configId;
    private WotValidationConfig config;

    @Before
    public void setUp() {
        final Config actorSystemConfig = ConfigFactory.parseString(
                "akka.actor.provider = cluster\n" +
                "akka.remote.artery.canonical.port = 0\n" +
                "akka.cluster.roles = [wot-validation-config-aware]\n" +
                "akka.persistence.journal.plugin = \"pekko-contrib-mongodb-persistence-wot-validation-config-journal\"\n" +
                "akka.persistence.snapshot-store.plugin = \"pekko-contrib-mongodb-persistence-wot-validation-config-snapshots\""
        ).withFallback(ConfigFactory.load("test.conf"));
        actorSystem = (ExtendedActorSystem) ActorSystem.create("test", actorSystemConfig);
        underTest = new WotValidationConfigMongoEventAdapter(actorSystem);
        configId = WotValidationConfigId.of("ns:test-id");
        config = WotValidationConfig.of(
                configId,
                true,
                false,
                ThingValidationConfig.of(
                        ThingValidationEnforceConfig.of(true, true, true, true, true),
                        ThingValidationForbidConfig.of(false, true, false, true)
                ),
                FeatureValidationConfig.of(
                        FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true),
                        FeatureValidationForbidConfig.of(false, true, false, true, false, true)
                ),
                Collections.emptyList(),
                WotValidationConfigRevision.of(1L),
                Instant.now(),
                Instant.now(),
                false,
                null
        );
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void testManifest() {
        final Metadata metadata = MetadataModelFactory.emptyMetadata();
        assertThat(underTest.manifest(WotValidationConfigCreated.of(configId, config, 1L, Instant.now(), DittoHeaders.empty(), metadata)))
                .isEqualTo(WotValidationConfigCreated.TYPE);
        assertThat(underTest.manifest(WotValidationConfigModified.of(configId, config, 2L, Instant.now(), DittoHeaders.empty(), metadata)))
                .isEqualTo(WotValidationConfigModified.TYPE);
        assertThat(underTest.manifest(WotValidationConfigDeleted.of(configId, 3L, Instant.now(), DittoHeaders.empty(), metadata)))
                .isEqualTo(WotValidationConfigDeleted.TYPE);
    }

    @Test
    public void testToJournalCreated() {
        // Given
        final Metadata metadata = MetadataModelFactory.emptyMetadata();
        final WotValidationConfigCreated event = WotValidationConfigCreated.of(configId, config, 1L, Instant.now(), DittoHeaders.empty(), metadata);

        // When
        final Object journalValue = underTest.toJournal(event);
        assertThat(journalValue).isInstanceOf(org.apache.pekko.persistence.journal.Tagged.class);
        final org.apache.pekko.persistence.journal.Tagged tagged = (org.apache.pekko.persistence.journal.Tagged) journalValue;
        final BsonDocument document = (BsonDocument) tagged.payload();

        // Then
        assertThat(document).isNotNull();
        assertThat(document.getString("type").getValue()).isEqualTo(WotValidationConfigCreated.TYPE);
        assertThat(document.getDocument("config")).isNotNull();
        assertThat(document.get("revision").asNumber().longValue()).isEqualTo(1L);
    }

    @Test
    public void testToJournalModified() {
        // Given
        final Metadata metadata = MetadataModelFactory.emptyMetadata();
        final WotValidationConfigModified event = WotValidationConfigModified.of(configId, config, 2L, Instant.now(), DittoHeaders.empty(), metadata);

        // When
        final Object journalValue = underTest.toJournal(event);
        assertThat(journalValue).isInstanceOf(org.apache.pekko.persistence.journal.Tagged.class);
        final org.apache.pekko.persistence.journal.Tagged tagged = (org.apache.pekko.persistence.journal.Tagged) journalValue;
        final BsonDocument document = (BsonDocument) tagged.payload();

        // Then
        assertThat(document).isNotNull();
        assertThat(document.getString("type").getValue()).isEqualTo(WotValidationConfigModified.TYPE);
        assertThat(document.getDocument("config")).isNotNull();
    }

    @Test
    public void testToJournalDeleted() {
        // Given
        final Metadata metadata = MetadataModelFactory.emptyMetadata();
        final WotValidationConfigDeleted event = WotValidationConfigDeleted.of(configId, 3L, Instant.now(), DittoHeaders.empty(), metadata);

        // When
        final Object journalValue = underTest.toJournal(event);
        assertThat(journalValue).isInstanceOf(org.apache.pekko.persistence.journal.Tagged.class);
        final org.apache.pekko.persistence.journal.Tagged tagged = (org.apache.pekko.persistence.journal.Tagged) journalValue;
        final BsonDocument document = (BsonDocument) tagged.payload();

        // Then
        assertThat(document).isNotNull();
        assertThat(document.getString("type").getValue()).isEqualTo(WotValidationConfigDeleted.TYPE);
        assertThat(document.getString("configId").getValue()).isEqualTo(configId.toString());
    }

    @Test
    public void testFromJournalCreated() {
        // Given
        final Metadata metadata = MetadataModelFactory.emptyMetadata();
        final WotValidationConfigCreated event = WotValidationConfigCreated.of(configId, config, 1L, Instant.now(), DittoHeaders.empty(), metadata);
        final Object journalValue = underTest.toJournal(event);
        final org.apache.pekko.persistence.journal.Tagged tagged = (org.apache.pekko.persistence.journal.Tagged) journalValue;

        // When
        final Object fromJournal = underTest.fromJournal(tagged.payload(), WotValidationConfigCreated.TYPE);
        assertThat(fromJournal).isInstanceOf(org.apache.pekko.persistence.journal.SingleEventSeq.class);
        final org.apache.pekko.persistence.journal.SingleEventSeq seq = (org.apache.pekko.persistence.journal.SingleEventSeq) fromJournal;
        final Object eventObj = seq.event();

        // Then
        assertThat(eventObj).isInstanceOf(WotValidationConfigCreated.class);
        final WotValidationConfigCreated created = (WotValidationConfigCreated) eventObj;
        assertThat((Object) created.getEntityId()).isEqualTo(configId);
    }

    @Test
    public void testFromJournalModified() {
        // Given
        final Metadata metadata = MetadataModelFactory.emptyMetadata();
        final WotValidationConfigModified event = WotValidationConfigModified.of(configId, config, 2L, Instant.now(), DittoHeaders.empty(), metadata);
        final Object journalValue = underTest.toJournal(event);
        final org.apache.pekko.persistence.journal.Tagged tagged = (org.apache.pekko.persistence.journal.Tagged) journalValue;

        // When
        final Object fromJournal = underTest.fromJournal(tagged.payload(), WotValidationConfigModified.TYPE);
        assertThat(fromJournal).isInstanceOf(org.apache.pekko.persistence.journal.SingleEventSeq.class);
        final org.apache.pekko.persistence.journal.SingleEventSeq seq = (org.apache.pekko.persistence.journal.SingleEventSeq) fromJournal;
        final Object eventObj = seq.event();

        // Then
        assertThat(eventObj).isInstanceOf(WotValidationConfigModified.class);
        final WotValidationConfigModified modified = (WotValidationConfigModified) eventObj;
        assertThat((Object) modified.getEntityId()).isEqualTo(configId);
    }

    @Test
    public void testFromJournalDeleted() {
        // Given
        final Metadata metadata = MetadataModelFactory.emptyMetadata();
        final WotValidationConfigDeleted event = WotValidationConfigDeleted.of(configId, 3L, Instant.now(), DittoHeaders.empty(), metadata);
        final Object journalValue = underTest.toJournal(event);
        final org.apache.pekko.persistence.journal.Tagged tagged = (org.apache.pekko.persistence.journal.Tagged) journalValue;

        // When
        final Object fromJournal = underTest.fromJournal(tagged.payload(), WotValidationConfigDeleted.TYPE);
        assertThat(fromJournal).isInstanceOf(org.apache.pekko.persistence.journal.SingleEventSeq.class);
        final org.apache.pekko.persistence.journal.SingleEventSeq seq = (org.apache.pekko.persistence.journal.SingleEventSeq) fromJournal;
        final Object eventObj = seq.event();

        // Then
        assertThat(eventObj).isInstanceOf(WotValidationConfigDeleted.class);
        final WotValidationConfigDeleted deleted = (WotValidationConfigDeleted) eventObj;
        assertThat((Object) deleted.getEntityId()).isEqualTo(configId);
    }
} 