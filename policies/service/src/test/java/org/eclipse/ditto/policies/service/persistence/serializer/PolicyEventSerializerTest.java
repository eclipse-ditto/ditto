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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistence.mongo.adapters.DelegatingMongoEventAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Tests {@link PolicyEventSerializer}: the manifest-keyed discard of removed legacy policy event types, and the class
 * as an extension point whose subclasses add their own journal migration.
 * <p>
 * Every legacy manifest is fed a <em>valid, current</em> {@code PolicyCreated} JSON, so without the discard guard the
 * payload would parse to an event; an empty result therefore proves the guard, not a parse failure.
 */
@RunWith(Enclosed.class)
public final class PolicyEventSerializerTest {

    /** A copy of the private {@code PolicyEventSerializer.LEGACY_DISCARDED_EVENT_TYPES}. */
    private static final List<String> LEGACY_MANIFESTS = List.of(
            "policies:importsAliasCreated",
            "policies:importsAliasModified",
            "policies:importsAliasDeleted",
            "policies:importsAliasSubjectCreated",
            "policies:importsAliasSubjectModified",
            "policies:importsAliasSubjectDeleted",
            "policies:importsAliasSubjectsModified",
            "policies:importsAliasesModified",
            "policies:importsAliasesDeleted",
            "policies:policyEntryAllowedImportAdditionsModified",
            "policies:policyImportEntriesAdditionsModified",
            "policies:policyImportEntryAdditionCreated",
            "policies:policyImportEntryAdditionModified",
            "policies:policyImportEntryAdditionDeleted"
    );

    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");

    private static final ExtendedActorSystem SYSTEM =
            (ExtendedActorSystem) ActorSystem.create("PolicyEventSerializerTest", ConfigFactory.load("test"));

    /** Recovery resets the event revision to the default; the persistence layer supplies the real one. */
    private static final PolicyCreated POLICY_CREATED =
            PolicyCreated.of(policy(), 0L, TIMESTAMP, DittoHeaders.empty(), null);

    private static final JsonObject POLICY_CREATED_JSON =
            new PolicyEventSerializer(SYSTEM).toJournalJson(POLICY_CREATED);

    private PolicyEventSerializerTest() {
        throw new AssertionError();
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(SYSTEM);
    }

    @RunWith(Parameterized.class)
    public static final class LegacyManifest {

        @Parameterized.Parameters(name = "{0}")
        public static List<String> manifests() {
            return LEGACY_MANIFESTS;
        }

        @Parameterized.Parameter
        public String manifest;

        private final LogCapture logCapture = new LogCapture();

        @Before
        public void startCapture() {
            logCapture.start();
        }

        @After
        public void stopCapture() {
            logCapture.stop();
        }

        @Test
        public void validPayloadUnderALegacyManifestIsDiscardedWithOneInfoLine() {
            final EventSeq result = new PolicyEventSerializer(SYSTEM).fromJournalJson(POLICY_CREATED_JSON, manifest);

            assertThat(CollectionConverters.asJava(result.events())).isEmpty();
            logCapture.assertSingleDiscardLineFor(manifest);
        }

        @Test
        public void discardsALegacyManifest() {
            assertThat(new PolicyEventSerializer(SYSTEM).discards(manifest)).isTrue();
        }

        @Test
        public void aSubclassDiscardsTheSameManifest() {
            final PolicyEventSerializer subclass = new PolicyEventSerializer(SYSTEM) {};

            final EventSeq result = subclass.fromJournalJson(POLICY_CREATED_JSON, manifest);

            assertThat(CollectionConverters.asJava(result.events())).isEmpty();
            logCapture.assertSingleDiscardLineFor(manifest);
        }

    }

    public static final class CurrentManifest {

        @Test
        public void theCurrentManifestParsesTheEvent() {
            assertParsesToPolicyCreated(new PolicyEventSerializer(SYSTEM), PolicyCreated.TYPE);
        }

        @Test
        public void aNullManifestParsesTheEvent() {
            assertParsesToPolicyCreated(new PolicyEventSerializer(SYSTEM), null);
        }

        @Test
        public void doesNotDiscardTheCurrentManifest() {
            assertThat(new PolicyEventSerializer(SYSTEM).discards(PolicyCreated.TYPE)).isFalse();
        }

        @Test
        public void doesNotDiscardANullManifest() {
            assertThat(new PolicyEventSerializer(SYSTEM).discards(null)).isFalse();
        }

        @Test
        public void aSubclassParsesTheCurrentManifest() {
            assertParsesToPolicyCreated(new PolicyEventSerializer(SYSTEM) {}, PolicyCreated.TYPE);
        }

        private static void assertParsesToPolicyCreated(final PolicyEventSerializer serializer,
                @Nullable final String manifest) {

            final EventSeq result = serializer.fromJournalJson(POLICY_CREATED_JSON, manifest);

            assertThat(CollectionConverters.asJava(result.events())).containsExactly(POLICY_CREATED);
        }

    }

    /**
     * The extension recipe of {@link PolicyEventSerializer}: a subclass overriding the journal migration hooks, and a
     * one-line event adapter naming that subclass.
     */
    public static final class ExtensionPoint {

        private static final String EXTENSION_FIELD = "extension";

        private final MigratingPolicyEventSerializer underTest = new MigratingPolicyEventSerializer(SYSTEM);

        @Before
        public void resetHookCalls() {
            MigratingPolicyEventSerializer.FROM_JOURNAL_HOOK_CALLS.set(0);
        }

        @Test
        public void toJournalJsonRunsTheSubclassMigration() {
            final JsonObject journalJson = underTest.toJournalJson(POLICY_CREATED);

            assertThat(journalJson.getValue(EXTENSION_FIELD)).contains(JsonValue.of(true));
            assertThat(journalJson.remove(EXTENSION_FIELD)).isEqualTo(POLICY_CREATED_JSON);
        }

        @Test
        public void fromJournalJsonRunsTheSubclassMigrationExactlyOnce() {
            final JsonObject journalJson = underTest.toJournalJson(POLICY_CREATED);

            final EventSeq result = underTest.fromJournalJson(journalJson, PolicyCreated.TYPE);

            assertThat(CollectionConverters.asJava(result.events())).containsExactly(POLICY_CREATED);
            assertThat(MigratingPolicyEventSerializer.FROM_JOURNAL_HOOK_CALLS).hasValue(1);
        }

        @Test
        public void aLegacyManifestIsDiscardedBeforeTheSubclassMigration() {
            final JsonObject journalJson = underTest.toJournalJson(POLICY_CREATED);

            for (final String legacyManifest : LEGACY_MANIFESTS) {
                final EventSeq result = underTest.fromJournalJson(journalJson, legacyManifest);

                assertThat(CollectionConverters.asJava(result.events())).as(legacyManifest).isEmpty();
            }
            assertThat(MigratingPolicyEventSerializer.FROM_JOURNAL_HOOK_CALLS).hasValue(0);
        }

        @Test
        public void theCurrentManifestIsNotDiscarded() {
            final JsonObject journalJson = underTest.toJournalJson(POLICY_CREATED);

            final EventSeq result = underTest.fromJournalJson(journalJson, PolicyCreated.TYPE);

            assertThat(CollectionConverters.asJava(result.events())).containsExactly(POLICY_CREATED);
        }

        @Test
        public void aNullManifestIsNotDiscardedAndRunsTheSubclassMigration() {
            final JsonObject journalJson = underTest.toJournalJson(POLICY_CREATED);

            final EventSeq result = underTest.fromJournalJson(journalJson, null);

            assertThat(CollectionConverters.asJava(result.events())).containsExactly(POLICY_CREATED);
            assertThat(MigratingPolicyEventSerializer.FROM_JOURNAL_HOOK_CALLS).hasValue(1);
        }

        @Test
        public void aOneLineMongoAdapterNamingTheSubclassRoundTripsAnEventThroughItsMigration() {
            final DelegatingMongoEventAdapter adapter = new MigratingPolicyMongoEventAdapter(SYSTEM);

            final Object journal = adapter.toJournal(POLICY_CREATED);

            assertThat(journal).isInstanceOf(Tagged.class);
            final Object stored = ((Tagged) journal).payload();
            assertThat(stored).isInstanceOf(BsonDocument.class);
            assertThat(((BsonDocument) stored).get(EXTENSION_FIELD)).isEqualTo(BsonBoolean.TRUE);

            final EventSeq recovered = adapter.fromJournal(stored, adapter.manifest(POLICY_CREATED));

            assertThat(CollectionConverters.asJava(recovered.events())).containsExactly(POLICY_CREATED);
            assertThat(MigratingPolicyEventSerializer.FROM_JOURNAL_HOOK_CALLS).hasValue(1);
        }

        @Test
        public void aOneLineMongoAdapterNamingTheSubclassDiscardsALegacyManifestBeforeTheSubclassMigration() {
            final DelegatingMongoEventAdapter adapter = new MigratingPolicyMongoEventAdapter(SYSTEM);
            final Object stored = ((Tagged) adapter.toJournal(POLICY_CREATED)).payload();

            final EventSeq recovered = adapter.fromJournal(stored, LEGACY_MANIFESTS.get(0));

            assertThat(CollectionConverters.asJava(recovered.events())).isEmpty();
            assertThat(MigratingPolicyEventSerializer.FROM_JOURNAL_HOOK_CALLS).hasValue(0);
        }

        /**
         * A serializer subclass with both journal migration hooks: it marks every written event with the field
         * {@code extension} and, on read, counts the call, requires the mark and strips it again. Event adapters
         * instantiate it by class name through its public {@code (ExtendedActorSystem)} constructor.
         */
        public static final class MigratingPolicyEventSerializer extends PolicyEventSerializer {

            /** Counts the from-journal hook calls of all instances: an adapter creates its instance by name. */
            static final AtomicInteger FROM_JOURNAL_HOOK_CALLS = new AtomicInteger();

            public MigratingPolicyEventSerializer(final ExtendedActorSystem system) {
                super(system);
            }

            @Override
            protected JsonObjectBuilder performToJournalMigration(final Event<?> event, final JsonObject jsonObject) {
                return super.performToJournalMigration(event, jsonObject).set(EXTENSION_FIELD, true);
            }

            @Override
            protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
                FROM_JOURNAL_HOOK_CALLS.incrementAndGet();
                assertThat(jsonObject.getValue(EXTENSION_FIELD)).contains(JsonValue.of(true));
                return super.performFromJournalMigration(jsonObject.remove(EXTENSION_FIELD));
            }

        }

        /** The one-line event adapter of the extension recipe: all it does is name the serializer subclass. */
        public static final class MigratingPolicyMongoEventAdapter extends DelegatingMongoEventAdapter {

            public MigratingPolicyMongoEventAdapter(final ExtendedActorSystem system) {
                super(system, MigratingPolicyEventSerializer.class.getName());
            }

        }

    }

    /**
     * Captures every line reaching the root logger, with the {@link PolicyEventSerializer} logger at INFO.
     */
    static final class LogCapture {

        private final Logger serializerLogger = (Logger) LoggerFactory.getLogger(PolicyEventSerializer.class);
        private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        @Nullable private Level previousLevel;

        void start() {
            previousLevel = serializerLogger.getLevel();
            serializerLogger.setLevel(Level.INFO);
            appender.start();
            rootLogger.addAppender(appender);
        }

        void stop() {
            rootLogger.detachAppender(appender);
            appender.stop();
            serializerLogger.setLevel(previousLevel);
        }

        void assertSingleDiscardLineFor(final String manifest) {
            final List<ILoggingEvent> serializerInfoLines = appender.list.stream()
                    .filter(event -> PolicyEventSerializer.class.getName().equals(event.getLoggerName()))
                    .filter(event -> event.getLevel() == Level.INFO)
                    .toList();
            assertThat(serializerInfoLines)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("Discarding legacy policy event of removed type <" + manifest +
                            "> during journal recovery.");
            assertThat(appender.list)
                    .noneMatch(event -> event.getLevel() == Level.ERROR);
        }

    }

    static Policy policy() {
        return PoliciesModelFactory.newPolicyBuilder(PolicyId.of("serializer:policy"))
                .forLabel("DEFAULT")
                .setSubject(SubjectIssuer.GOOGLE, "serializer-subject")
                .setGrantedPermissions("policy", "/", "READ", "WRITE")
                .setLifecycle(PolicyLifecycle.ACTIVE)
                .setRevision(1L)
                .setModified(TIMESTAMP)
                .setCreated(TIMESTAMP)
                .build();
    }

}
