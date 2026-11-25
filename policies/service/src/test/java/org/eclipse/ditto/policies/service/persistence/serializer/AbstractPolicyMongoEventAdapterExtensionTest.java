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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Tests that {@link AbstractPolicyMongoEventAdapter} stays an extension point: a subclass's to-journal and
 * from-journal migration hooks run, and a legacy manifest is still discarded before the from-journal hook.
 */
public final class AbstractPolicyMongoEventAdapterExtensionTest {

    private static final String EXTENSION_FIELD = "extension";

    @Nullable private static ExtendedActorSystem system;

    private final AtomicInteger fromJournalHookCalls = new AtomicInteger();

    private final AbstractPolicyMongoEventAdapter underTest = new AbstractPolicyMongoEventAdapter(system) {

        @Override
        protected JsonObjectBuilder performToJournalMigration(final Event<?> event, final JsonObject jsonObject) {
            return super.performToJournalMigration(event, jsonObject).set(EXTENSION_FIELD, true);
        }

        @Override
        protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
            fromJournalHookCalls.incrementAndGet();
            assertThat(jsonObject.getValue(EXTENSION_FIELD)).contains(JsonValue.of(true));
            return super.performFromJournalMigration(jsonObject.remove(EXTENSION_FIELD));
        }

    };

    @BeforeClass
    public static void startActorSystem() {
        system = (ExtendedActorSystem) ActorSystem.create("AbstractPolicyMongoEventAdapterExtensionTest",
                ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void toJournalRunsTheSubclassMigration() {
        final BsonDocument written = writtenPolicyCreated();

        assertThat(written.get(EXTENSION_FIELD)).isEqualTo(BsonBoolean.TRUE);
    }

    @Test
    public void fromJournalRunsTheSubclassMigration() {
        final BsonDocument written = writtenPolicyCreated();

        final List<Object> events =
                CollectionConverters.asJava(underTest.fromJournal(written, PolicyCreated.TYPE).events());

        assertThat(events).containsExactly(recoveredPolicyCreated());
        assertThat(fromJournalHookCalls).hasValue(1);
    }

    @Test
    public void aLegacyManifestIsStillDiscarded() {
        final BsonDocument written = writtenPolicyCreated();

        final List<Object> events = CollectionConverters.asJava(
                underTest.fromJournal(written, PolicyMongoEventAdapterGoldenTest.LEGACY_MANIFEST).events());

        assertThat(events).isEmpty();
        assertThat(fromJournalHookCalls).hasValue(0);
    }

    @Test
    public void theCurrentManifestStillParses() {
        final BsonDocument written = writtenPolicyCreated();

        final List<Object> events =
                CollectionConverters.asJava(underTest.fromJournal(written, PolicyCreated.TYPE).events());

        assertThat(events).containsExactly(recoveredPolicyCreated());
    }

    private BsonDocument writtenPolicyCreated() {
        final PolicyEvent<?> policyCreated = PolicyMongoEventAdapterGoldenTest.writtenEvents().get(0);
        return (BsonDocument) ((Tagged) underTest.toJournal(policyCreated)).payload();
    }

    private static Object recoveredPolicyCreated() {
        return PolicyMongoEventAdapterGoldenTest.recoveredEvents().get(0);
    }

}
