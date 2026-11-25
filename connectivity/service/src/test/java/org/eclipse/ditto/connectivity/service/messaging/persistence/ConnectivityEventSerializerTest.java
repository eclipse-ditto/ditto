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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionModified;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
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
 * Tests {@link ConnectivityEventSerializer}: field encryption on write, decryption with the current or the old key on
 * read, and the {@link ConnectionMigrationUtil} parse strategies of its event registry.
 */
public final class ConnectivityEventSerializerTest {

    /** Fixed key already used by the connectivity tests. */
    static final String K_OLD = "vJFSTPE9PO2BtZlcMAwNjs8jdFvQCk0Ya9MVdYjRJUU=";
    /** Generated once with {@code EncryptorAesGcm.generateAESKeyAsString()}. */
    static final String K_NEW = "cn5N7Ykci9vET2VuANQ-CedGexxA1LcAsQvJeAM01qk=";

    private static final String PLAIN_URI = "amqps://user:golden-secret@golden-host:5671";
    private static final Instant TIMESTAMP = Instant.parse("2026-11-19T00:00:00Z");
    private static final JsonPointer URI_POINTER = JsonPointer.of("/connection/uri");
    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
            AuthorizationSubject.newInstance("serializer:subject"));

    private final List<ActorSystem> systems = new ArrayList<>();

    @After
    public void shutdownSystems() {
        systems.forEach(TestKit::shutdownActorSystem);
    }

    @Test
    public void encryptsOnWriteWithTheCurrentKeyAndDecryptsOnRead() {
        final ConnectivityEventSerializer underTest = serializer(true, K_OLD, null);
        final ConnectionCreated event = connectionCreated(connection(PLAIN_URI));

        final JsonObject journalJson = underTest.toJournalJson(event);

        final String storedUri = journalJson.getValue(URI_POINTER).map(JsonValue::asString).orElseThrow();
        assertThat(storedUri).isNotEqualTo(PLAIN_URI)
                .startsWith("amqps://user:" + JsonFieldsEncryptor.ENCRYPTED_PREFIX)
                .endsWith("@golden-host:5671");
        assertThat(recoveredUri(underTest, journalJson)).isEqualTo(PLAIN_URI);
    }

    @Test
    public void decryptsDataOfTheOldKeyWithTheFallbackKey() {
        final JsonObject encryptedWithOldKey =
                serializer(true, K_OLD, null).toJournalJson(connectionCreated(connection(PLAIN_URI)));

        final ConnectivityEventSerializer rotated = serializer(true, K_NEW, K_OLD);

        assertThat(recoveredUri(rotated, encryptedWithOldKey)).isEqualTo(PLAIN_URI);
    }

    @Test
    public void decryptsDataOfTheCurrentKeyWithoutAnOldKey() {
        final JsonObject encrypted =
                serializer(true, K_OLD, null).toJournalJson(connectionCreated(connection(PLAIN_URI)));

        assertThat(recoveredUri(serializer(true, K_OLD, null), encrypted)).isEqualTo(PLAIN_URI);
    }

    @Test
    public void writesThePlaintextWhenEncryptionIsOff() {
        final ConnectivityEventSerializer underTest = serializer(false, null, null);

        final JsonObject journalJson = underTest.toJournalJson(connectionCreated(connection(PLAIN_URI)));

        assertThat(journalJson.getValue(URI_POINTER)).contains(JsonValue.of(PLAIN_URI));
        assertThat(recoveredUri(underTest, journalJson)).isEqualTo(PLAIN_URI);
    }

    @Test
    public void connectionCreatedInALegacyShapeIsMigratedOnRead() {
        final JsonObject legacyJson = legacyJson(connectionCreated(connectionWithSource()).toJson());

        final ConnectivityEvent<?> recovered =
                recover(serializer(false, null, null), legacyJson, ConnectionCreated.TYPE);

        assertThat(recovered).isInstanceOf(ConnectionCreated.class);
        assertMigratedSourceFilters(((ConnectionCreated) recovered).getConnection());
    }

    @Test
    public void connectionModifiedInALegacyShapeIsMigratedOnRead() {
        final ConnectionModified modified =
                ConnectionModified.of(connectionWithSource(), 2L, TIMESTAMP, DittoHeaders.empty(), null);
        final JsonObject legacyJson = legacyJson(modified.toJson());

        final ConnectivityEvent<?> recovered =
                recover(serializer(false, null, null), legacyJson, ConnectionModified.TYPE);

        assertThat(recovered).isInstanceOf(ConnectionModified.class);
        assertMigratedSourceFilters(((ConnectionModified) recovered).getConnection());
    }

    @Test
    public void thePlainGlobalRegistryDoesNotMigrateTheLegacyShape() {
        final JsonObject legacyJson = legacyJson(connectionCreated(connectionWithSource()).toJson());

        final ConnectionCreated parsed = (ConnectionCreated) GlobalEventRegistry.getInstance()
                .parse(legacyJson, DittoHeaders.empty());

        assertThat(parsed.getConnection().getSources().get(0).getEnforcement()).isEmpty();
    }

    @Test
    public void theLoggerConstructorLogsTheEncryptionFlagWithTheGivenLogger() {
        final String givenLoggerName = "org.eclipse.ditto.connectivity.service.messaging.persistence.GivenLogger";
        final Logger givenLogger = (Logger) LoggerFactory.getLogger(givenLoggerName);
        final Logger classLogger = (Logger) LoggerFactory.getLogger(ConnectivityEventSerializer.class);
        final Level givenLevel = givenLogger.getLevel();
        final Level classLevel = classLogger.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        givenLogger.setLevel(Level.INFO);
        classLogger.setLevel(Level.INFO);
        givenLogger.addAppender(appender);
        classLogger.addAppender(appender);
        try {
            final ActorSystem system = ActorSystem.create("ConnectivityEventSerializerTest",
                    ConfigFactory.parseString(encryptionBlock(true, K_OLD, null))
                            .withFallback(ConfigFactory.load("test")));
            systems.add(system);
            new ConnectivityEventSerializer((ExtendedActorSystem) system, givenLogger);
        } finally {
            givenLogger.detachAppender(appender);
            classLogger.detachAppender(appender);
            givenLogger.setLevel(givenLevel);
            classLogger.setLevel(classLevel);
            appender.stop();
        }

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getLoggerName, ILoggingEvent::getFormattedMessage)
                .containsExactly(tuple(givenLoggerName, "Connections fields encryption: true"));
    }

    private ConnectivityEventSerializer serializer(final boolean enabled, @Nullable final String key,
            @Nullable final String oldKey) {
        final ActorSystem system = ActorSystem.create("ConnectivityEventSerializerTest",
                ConfigFactory.parseString(encryptionBlock(enabled, key, oldKey))
                        .withFallback(ConfigFactory.load("test")));
        systems.add(system);
        return new ConnectivityEventSerializer((ExtendedActorSystem) system);
    }

    static String encryptionBlock(final boolean enabled, @Nullable final String key, @Nullable final String oldKey) {
        return "ditto.connectivity.connection.encryption {\n"
                + "  encryption-enabled = " + enabled + "\n"
                + "  symmetrical-key = \"" + (key == null ? "" : key) + "\"\n"
                + "  old-symmetrical-key = \"" + (oldKey == null ? "" : oldKey) + "\"\n"
                + "  json-pointers = [\"/uri\", \"/credentials/password\"]\n"
                + "}\n";
    }

    private static String recoveredUri(final ConnectivityEventSerializer serializer, final JsonObject journalJson) {
        final ConnectivityEvent<?> recovered = recover(serializer, journalJson, ConnectionCreated.TYPE);
        return ((ConnectionCreated) recovered).getConnection().getUri();
    }

    private static ConnectivityEvent<?> recover(final ConnectivityEventSerializer serializer,
            final JsonObject journalJson, final String manifest) {
        final List<Object> events =
                CollectionConverters.asJava(serializer.fromJournalJson(journalJson, manifest).events());
        assertThat(events).hasSize(1);
        return (ConnectivityEvent<?>) events.get(0);
    }

    private static ConnectionCreated connectionCreated(final Connection connection) {
        return ConnectionCreated.of(connection, 1L, TIMESTAMP, DittoHeaders.empty(), null);
    }

    private static Connection connection(final String uri) {
        return ConnectivityModelFactory.newConnectionBuilder(ConnectionId.of("serializer-connection"),
                        ConnectionType.AMQP_10, ConnectivityStatus.CLOSED, uri)
                .targets(List.of(ConnectivityModelFactory.newTargetBuilder()
                        .address("serializer/target")
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .topics(Topic.TWIN_EVENTS)
                        .build()))
                .build();
    }

    private static Connection connectionWithSource() {
        return ConnectivityModelFactory.newConnectionBuilder(ConnectionId.of("serializer-connection"),
                        ConnectionType.AMQP_10, ConnectivityStatus.CLOSED, PLAIN_URI)
                .sources(List.of(ConnectivityModelFactory.newSourceBuilder()
                        .address("serializer/source")
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .build()))
                .build();
    }

    /**
     * Puts the connection's first source into the pre-enforcement shape that {@code MigrateSourceFilters} migrates:
     * a top-level {@code filters} array instead of an {@code enforcement} object.
     */
    private static JsonObject legacyJson(final JsonObject eventJson) {
        final JsonPointer sourcesPointer = JsonPointer.of("/connection/sources");
        final JsonArray sources = eventJson.getValue(sourcesPointer).map(JsonValue::asArray).orElseThrow();
        final JsonObject legacySource = sources.get(0).map(JsonValue::asObject).orElseThrow().toBuilder()
                .set("filters", JsonArray.of(JsonValue.of("{{thing:id}}")))
                .build();
        return eventJson.setValue(sourcesPointer, JsonArray.of(legacySource));
    }

    private static void assertMigratedSourceFilters(final Connection connection) {
        final Source source = connection.getSources().get(0);
        assertThat(source.getEnforcement()).isPresent();
        assertThat(source.getEnforcement().map(enforcement -> enforcement.getFilters()).orElseThrow())
                .containsExactly("{{thing:id}}");
    }

}
