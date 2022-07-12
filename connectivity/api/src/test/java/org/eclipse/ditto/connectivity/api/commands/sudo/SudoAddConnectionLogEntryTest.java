/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoAddConnectionLogEntry}.
 */
public final class SudoAddConnectionLogEntryTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();
    
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private LogEntry logEntry;

    @Before
    public void before() {
        logEntry = ConnectivityModelFactory.newLogEntryBuilder(testNameCorrelationId.getCorrelationId().toString(),
                        Instant.now(),
                        LogCategory.RESPONSE,
                        LogType.DROPPED,
                        LogLevel.FAILURE,
                        "Facere culpa et cum doloribus. Voluptas alias magni voluptatum.")
                .entityId(ThingId.generateRandom())
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoAddConnectionLogEntry.class, areImmutable(), provided(LogEntry.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoAddConnectionLogEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithNullLogEntryThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, null, DITTO_HEADERS))
                .withMessage("The logEntry must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithNullConnectionIdThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SudoAddConnectionLogEntry.newInstance(null, logEntry, DITTO_HEADERS))
                .withMessage("The connectionId must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceReturnsNotNull() {
        final var instance = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);

        assertThat(instance).isNotNull();
    }

    @Test
    public void getConnectionIdReturnsExpected() {
        final var underTest = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);

        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(CONNECTION_ID);
    }

    @Test
    public void getLogEntryReturnsExpected() {
        final var underTest = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);

        assertThat(underTest.getLogEntry()).isEqualTo(logEntry);
    }

    @Test
    public void toJsonReturnsExpected() {
        final var jsonObject = JsonObject.newBuilder()
                .set(Command.JsonFields.TYPE, SudoAddConnectionLogEntry.TYPE)
                .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, CONNECTION_ID.toString())
                .set(SudoAddConnectionLogEntry.JsonFields.LOG_ENTRY, logEntry.toJson())
                .build();

        final var underTest = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromJsonWithNullJsonObjectThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SudoAddConnectionLogEntry.fromJson(null, DITTO_HEADERS))
                .withNoCause();
    }

    @Test
    public void fromJsonWithValidJsonObjectReturnsExpected() {
        final var addConnectionLogEntry = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);

        assertThat(SudoAddConnectionLogEntry.fromJson(addConnectionLogEntry.toJson(), DITTO_HEADERS)).isEqualTo(addConnectionLogEntry);
    }

    @Test
    public void fromJsonWithMissingConnectionIdJsonFieldThrowsException() {
        final var invalidAddConnectionLogEntryJsonObject = JsonObject.newBuilder()
                .set(Command.JsonFields.TYPE, SudoAddConnectionLogEntry.TYPE)
                .set(SudoAddConnectionLogEntry.JsonFields.LOG_ENTRY, logEntry.toJson())
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> SudoAddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject, DITTO_HEADERS))
                .withMessageStartingWith("JSON did not include required <%s>",
                        ConnectivityCommand.JsonFields.JSON_CONNECTION_ID.getPointer());
    }

    @Test
    public void fromJsonWithMissingLogEntryJsonFieldThrowsException() {
        final var invalidAddConnectionLogEntryJsonObject = JsonObject.newBuilder()
                .set(Command.JsonFields.TYPE, SudoAddConnectionLogEntry.TYPE)
                .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, CONNECTION_ID.toString())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> SudoAddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject, DITTO_HEADERS))
                .withMessageStartingWith("Failed to deserialize value of key <%s>",
                        SudoAddConnectionLogEntry.JsonFields.LOG_ENTRY.getPointer());
    }

    @Test
    public void fromJsonWithInvalidConnectionIdValueTypeThrowsException() {
        final var connectionIdFieldDefinition = ConnectivityCommand.JsonFields.JSON_CONNECTION_ID;
        final var addConnectionLogEntry = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);
        final var invalidAddConnectionLogEntryJsonObject =
                JsonFactory.newObjectBuilder(addConnectionLogEntry.toJson())
                        .set(connectionIdFieldDefinition.getPointer(), JsonValue.of(42))
                        .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> SudoAddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject, DITTO_HEADERS));
    }

    @Test
    public void fromJsonWithCorruptedConnectionIdValueTypeThrowsException() {
        final var connectionIdFieldDefinition = ConnectivityCommand.JsonFields.JSON_CONNECTION_ID;
        final var addConnectionLogEntry = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);
        final String connectionId = "%myInvalidConnectionId%";
        final var invalidAddConnectionLogEntryJsonObject = JsonFactory.newObjectBuilder(addConnectionLogEntry.toJson())
                .set(connectionIdFieldDefinition.getPointer(), connectionId)
                .build();

        assertThatExceptionOfType(ConnectionIdInvalidException.class)
                .isThrownBy(() -> SudoAddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject, DITTO_HEADERS))
                .withMessageStartingWith("The Connection ID '%s' is not valid",
                        connectionId);
    }

    @Test
    public void fromJsonWithInvalidLogEntryValueTypeThrowsException() {
        final var logEntryFieldDefinition = SudoAddConnectionLogEntry.JsonFields.LOG_ENTRY;
        final var addConnectionLogEntry = SudoAddConnectionLogEntry.newInstance(CONNECTION_ID, logEntry, DITTO_HEADERS);
        final var invalidAddConnectionLogEntryJsonObject = JsonFactory.newObjectBuilder(addConnectionLogEntry.toJson())
                .set(logEntryFieldDefinition.getPointer(), "foo")
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> SudoAddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject, DITTO_HEADERS))
                .withMessageStartingWith("Failed to deserialize value of key <%s> as %s:",
                        logEntryFieldDefinition.getPointer(),
                        LogEntry.class.getName())
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void fromJsonWithCorruptedLogEntryValueThrowsException() {
        final var logEntryFieldDefinition = SudoAddConnectionLogEntry.JsonFields.LOG_ENTRY;
        final var invalidLogEntryJsonObject = JsonFactory.newObjectBuilder(logEntry.toJson())
                .set(LogEntry.JsonFields.LEVEL, "foo")
                .build();
        final var invalidAddConnectionLogEntryJsonObject = JsonObject.newBuilder()
                .set(Command.JsonFields.TYPE, SudoAddConnectionLogEntry.TYPE)
                .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, CONNECTION_ID.toString())
                .set(logEntryFieldDefinition, invalidLogEntryJsonObject)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> SudoAddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject, DITTO_HEADERS))
                .withMessageStartingWith("Failed to deserialize value of key <%s>",
                        logEntryFieldDefinition.getPointer());
    }

}
