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
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyModified;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link DefaultPolicyMongoEventAdapter} to {@code golden/journal/policy-mongo.json}: every
 * event is written with the stored manifest, tags and BSON payload, and every stored row replays to the recovered
 * event. A stored row of the removed legacy type {@code policies:importsAliasCreated}
 * ({@code golden/journal/policy-legacy-importsAliasCreated-mongo.json}) replays to no event with one INFO line.
 */
public final class PolicyMongoEventAdapterGoldenTest {

    static final String FIXTURE = "policy-mongo.json";
    static final String LEGACY_FIXTURE = "policy-legacy-importsAliasCreated-mongo.json";
    static final String LEGACY_MANIFEST = "policies:importsAliasCreated";

    private static final PolicyId POLICY_ID = PolicyId.of("golden:policy");
    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("golden-policy").build();

    @Nullable private static ExtendedActorSystem system;

    private final DefaultPolicyMongoEventAdapter underTest = new DefaultPolicyMongoEventAdapter(system);

    @BeforeClass
    public static void startActorSystem() {
        system = (ExtendedActorSystem) ActorSystem.create("PolicyMongoEventAdapterGoldenTest",
                ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final List<BsonDocument> rows = rows();
        final List<PolicyEvent<?>> written = writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final BsonDocument row = rows.get(i);
            final PolicyEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(row.getString("manifest").getValue());
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags())).isEqualTo(tags(row));
            assertThat(tagged.payload()).isEqualTo(row.getDocument("payload"));
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        final List<BsonDocument> rows = rows();
        final List<PolicyEvent<?>> recovered = recoveredEvents();
        assertThat(rows).hasSameSizeAs(recovered);

        for (int i = 0; i < rows.size(); i++) {
            final BsonDocument row = rows.get(i);
            assertThat(CollectionConverters.asJava(
                    underTest.fromJournal(row.getDocument("payload"), row.getString("manifest").getValue())
                            .events()))
                    .containsExactly(recovered.get(i));
        }
    }

    @Test
    public void replaysTheLegacyRowToNoEvent() throws IOException {
        final BsonDocument legacy = BsonDocument.parse(readFixture(LEGACY_FIXTURE));
        final DiscardLogCapture logCapture =
                new DiscardLogCapture(AbstractPolicyMongoEventAdapter.class.getName());
        logCapture.start();
        try {
            assertThat(CollectionConverters.asJava(underTest.fromJournal(legacy, LEGACY_MANIFEST).events()))
                    .isEmpty();
            logCapture.assertSingleDiscardLineFor(LEGACY_MANIFEST);
        } finally {
            logCapture.stop();
        }
    }

    /**
     * @return the events written to the fixture, in row order.
     */
    static List<PolicyEvent<?>> writtenEvents() {
        return List.of(PolicyCreated.of(policy("golden-subject", 1L), 1L, TIMESTAMP, HEADERS, null),
                PolicyModified.of(policy("modified-subject", 2L), 2L, TIMESTAMP, HEADERS, null),
                PolicyDeleted.of(POLICY_ID, 3L, TIMESTAMP, HEADERS, null));
    }

    /**
     * @return the events the fixture rows replay to, in row order: recovery resets the revision to the default and
     * restores only the persisted historical headers.
     */
    static List<PolicyEvent<?>> recoveredEvents() {
        return List.of(PolicyCreated.of(policy("golden-subject", 1L), 0L, TIMESTAMP, HEADERS, null),
                PolicyModified.of(policy("modified-subject", 2L), 0L, TIMESTAMP, HEADERS, null),
                PolicyDeleted.of(POLICY_ID, 0L, TIMESTAMP, HEADERS, null));
    }

    static String readFixture(final String name) throws IOException {
        try (final InputStream in = Objects.requireNonNull(
                PolicyMongoEventAdapterGoldenTest.class.getResourceAsStream("/golden/journal/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<BsonDocument> rows() throws IOException {
        return BsonDocument.parse(readFixture(FIXTURE)).getArray("rows").stream()
                .map(BsonValue::asDocument)
                .toList();
    }

    private static Set<String> tags(final BsonDocument row) {
        return row.getArray("tags").stream()
                .map(BsonValue::asString)
                .map(BsonString::getValue)
                .collect(Collectors.toSet());
    }

    private static Policy policy(final String subject, final long revision) {
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .forLabel("DEFAULT")
                .setSubject(SubjectIssuer.GOOGLE, subject)
                .setGrantedPermissions("thing", "/", "READ", "WRITE")
                .setGrantedPermissions("policy", "/", "READ", "WRITE")
                .setLifecycle(PolicyLifecycle.ACTIVE)
                .setRevision(revision)
                .setModified(TIMESTAMP)
                .setCreated(TIMESTAMP)
                .build();
    }

    /**
     * Captures every line reaching the root logger, with the logger expected to log the discard at INFO.
     */
    static final class DiscardLogCapture {

        private final String loggerName;
        private final Logger discardLogger;
        private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        @Nullable private Level previousLevel;

        DiscardLogCapture(final String loggerName) {
            this.loggerName = loggerName;
            discardLogger = (Logger) LoggerFactory.getLogger(loggerName);
        }

        void start() {
            previousLevel = discardLogger.getLevel();
            discardLogger.setLevel(Level.INFO);
            appender.start();
            rootLogger.addAppender(appender);
        }

        void stop() {
            rootLogger.detachAppender(appender);
            appender.stop();
            discardLogger.setLevel(previousLevel);
        }

        void assertSingleDiscardLineFor(final String manifest) {
            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.INFO)
                    .filteredOn(event -> event.getFormattedMessage().startsWith("Discarding legacy policy event"))
                    .extracting(ILoggingEvent::getLoggerName, ILoggingEvent::getFormattedMessage)
                    .containsExactly(tuple(loggerName,
                            "Discarding legacy policy event of removed type <" + manifest +
                                    "> during journal recovery."));
            assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
        }

    }

}
