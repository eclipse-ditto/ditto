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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionModified;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link ConnectivityMongoEventAdapter} to {@code golden/journal/connection-mongo.json},
 * written with field encryption on and key {@link ConnectionJournalEncryptionFixture#K_OLD}: every event is written
 * with the stored manifest and tags, rows with an encrypted connection never contain the plain secret (their payload
 * is not compared, the AES-GCM IV is random), and every stored row replays to the recovered event with the key it was
 * written with and after a key rotation that keeps it as the old key.
 */
public final class ConnectivityMongoEventAdapterGoldenTest {

    static final String FIXTURE = "connection-mongo.json";
    static final String SECRET = "golden-secret";
    static final String PLAIN_URI = "amqps://user:" + SECRET + "@golden-host:5671";
    static final String SERIALIZER_LOGGER_NAME =
            "org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectivityEventSerializer";

    private static final ConnectionId CONNECTION_ID = ConnectionId.of("golden-connection");
    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("golden-connection").build();
    private static final DittoHeaders HEADERS_WITH_TAGS = HEADERS.toBuilder()
            .journalTags(Set.of("always-alive", "priority-3"))
            .build();

    private final List<ActorSystem> systems = new ArrayList<>();

    @After
    public void shutdownSystems() {
        systems.forEach(TestKit::shutdownActorSystem);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final ConnectivityMongoEventAdapter underTest = adapter(ConnectionJournalEncryptionFixture.K_OLD, null);
        final List<BsonDocument> rows = rows();
        final List<ConnectivityEvent<?>> written = writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final BsonDocument row = rows.get(i);
            final ConnectivityEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(row.getString("manifest").getValue());
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags())).isEqualTo(tags(row));
            final BsonDocument payload = (BsonDocument) tagged.payload();
            if (row.getDocument("payload").containsKey("connection")) {
                assertThat(payload.toJson()).doesNotContain(SECRET);
            } else {
                assertThat(payload).isEqualTo(row.getDocument("payload"));
            }
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        assertThat(replay(adapter(ConnectionJournalEncryptionFixture.K_OLD, null)))
                .containsExactlyElementsOf(recoveredEvents());
    }

    @Test
    public void replaysWithTheKeyTheRowWasWrittenWith() throws IOException {
        assertReplaysToPlainUri(adapter(ConnectionJournalEncryptionFixture.K_OLD, null));
    }

    @Test
    public void replaysAfterAKeyRotationWithItsKeyAsTheOldKey() throws IOException {
        assertReplaysToPlainUri(
                adapter(ConnectionJournalEncryptionFixture.K_NEW, ConnectionJournalEncryptionFixture.K_OLD));
    }

    @Test
    public void oneArgumentFromJournalJsonDecrypts() throws IOException {
        final ConnectivityMongoEventAdapter underTest = adapter(ConnectionJournalEncryptionFixture.K_OLD, null);
        final BsonDocument created = rows().get(0).getDocument("payload");

        final List<Object> events = CollectionConverters.asJava(
                underTest.fromJournalJson(DittoBsonJson.getInstance().serialize(created).asObject()).events());

        assertThat(events).containsExactly(recoveredEvents().get(0));
    }

    @Test
    public void constructionLogsTheEncryptionFlagUnderTheAdapterLogger() {
        assertConstructionLogsTheEncryptionFlagUnder(ConnectivityMongoEventAdapter.class.getName(),
                () -> adapter(ConnectionJournalEncryptionFixture.K_OLD, null));
    }

    /**
     * @return the events written to the fixture, in row order; their headers carry journal tags.
     */
    static List<ConnectivityEvent<?>> writtenEvents() {
        return List.of(ConnectionCreated.of(connection("golden/target"), 1L, TIMESTAMP, HEADERS_WITH_TAGS, null),
                ConnectionModified.of(connection("golden/modified"), 2L, TIMESTAMP, HEADERS_WITH_TAGS, null),
                ConnectionDeleted.of(CONNECTION_ID, 3L, TIMESTAMP, HEADERS_WITH_TAGS, null));
    }

    /**
     * @return the events the fixture rows replay to, in row order: recovery resets the revision to the default and
     * restores only the persisted historical headers.
     */
    static List<ConnectivityEvent<?>> recoveredEvents() {
        return List.of(ConnectionCreated.of(connection("golden/target"), 0L, TIMESTAMP, HEADERS, null),
                ConnectionModified.of(connection("golden/modified"), 0L, TIMESTAMP, HEADERS, null),
                ConnectionDeleted.of(CONNECTION_ID, 0L, TIMESTAMP, HEADERS, null));
    }

    /**
     * Asserts that {@code construction} logs exactly one INFO {@code Connections fields encryption: true} under
     * {@code adapterLoggerName} and none under the serializer's logger name.
     */
    static void assertConstructionLogsTheEncryptionFlagUnder(final String adapterLoggerName,
            final Supplier<?> construction) {
        final Logger adapterLogger = (Logger) LoggerFactory.getLogger(adapterLoggerName);
        final Logger serializerLogger = (Logger) LoggerFactory.getLogger(SERIALIZER_LOGGER_NAME);
        final Level adapterLevel = adapterLogger.getLevel();
        final Level serializerLevel = serializerLogger.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        adapterLogger.setLevel(Level.INFO);
        serializerLogger.setLevel(Level.INFO);
        adapterLogger.addAppender(appender);
        serializerLogger.addAppender(appender);
        try {
            construction.get();
        } finally {
            adapterLogger.detachAppender(appender);
            serializerLogger.detachAppender(appender);
            adapterLogger.setLevel(adapterLevel);
            serializerLogger.setLevel(serializerLevel);
            appender.stop();
        }
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getLoggerName, ILoggingEvent::getFormattedMessage)
                .containsExactly(tuple(adapterLoggerName, "Connections fields encryption: true"));
    }

    static String readFixture(final String name) throws IOException {
        try (final InputStream in = Objects.requireNonNull(
                ConnectivityMongoEventAdapterGoldenTest.class.getResourceAsStream("/golden/journal/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static ExtendedActorSystem actorSystem(final String name, final String key, @Nullable final String oldKey) {
        return (ExtendedActorSystem) ActorSystem.create(name,
                ConfigFactory.parseString(ConnectionJournalEncryptionFixture.encryptionBlock(true, key, oldKey))
                        .withFallback(ConfigFactory.load("test")));
    }

    static void assertPlainUri(final List<Object> events) {
        events.stream()
                .filter(event -> !(event instanceof ConnectionDeleted))
                .map(event -> event instanceof ConnectionCreated created
                        ? created.getConnection()
                        : ((ConnectionModified) event).getConnection())
                .forEach(connection -> assertThat(connection.getUri()).isEqualTo(PLAIN_URI));
    }

    private static void assertReplaysToPlainUri(final ConnectivityMongoEventAdapter underTest) throws IOException {
        final List<Object> events = replay(underTest);
        assertThat(events).containsExactlyElementsOf(recoveredEvents());
        assertPlainUri(events);
    }

    private static List<Object> replay(final ConnectivityMongoEventAdapter underTest) throws IOException {
        final List<Object> events = new ArrayList<>();
        for (final BsonDocument row : rows()) {
            final List<Object> rowEvents = CollectionConverters.asJava(
                    underTest.fromJournal(row.getDocument("payload"), row.getString("manifest").getValue())
                            .events());
            assertThat(rowEvents).hasSize(1);
            events.addAll(rowEvents);
        }
        return events;
    }

    private ConnectivityMongoEventAdapter adapter(final String key, @Nullable final String oldKey) {
        final ExtendedActorSystem system = actorSystem("ConnectivityMongoEventAdapterGoldenTest", key, oldKey);
        systems.add(system);
        return new ConnectivityMongoEventAdapter(system);
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

    private static Connection connection(final String targetAddress) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.AMQP_10, ConnectivityStatus.CLOSED, PLAIN_URI)
                .targets(List.of(ConnectivityModelFactory.newTargetBuilder()
                        .address(targetAddress)
                        .authorizationContext(AuthorizationContext.newInstance(
                                DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                                AuthorizationSubject.newInstance("golden:subject")))
                        .topics(Topic.TWIN_EVENTS)
                        .build()))
                .build();
    }

}
