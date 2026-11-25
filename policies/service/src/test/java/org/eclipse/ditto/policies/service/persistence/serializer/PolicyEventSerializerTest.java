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

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
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
 * Tests {@link PolicyEventSerializer}: the manifest-keyed discard of removed legacy policy event types.
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
