/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link AddConnectionLogEntry}.
 */
public final class AddConnectionLogEntryTest {

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
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(AddConnectionLogEntry.class, areImmutable(), provided(LogEntry.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(AddConnectionLogEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithNullLogEntryThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> AddConnectionLogEntry.of(null))
                .withMessage("The connectionLogEntry must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceReturnsNotNull() {
        final AddConnectionLogEntry instance = AddConnectionLogEntry.of(logEntry);

        assertThat(instance).isNotNull();
    }

    @Test
    public void getLogEntryReturnsExpected() {
        final AddConnectionLogEntry underTest = AddConnectionLogEntry.of(logEntry);

        assertThat(underTest.getLogEntry()).isEqualTo(logEntry);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(AddConnectionLogEntry.JsonFields.LOG_ENTRY, logEntry.toJson())
                .build();

        final AddConnectionLogEntry underTest = AddConnectionLogEntry.of(logEntry);

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromJsonWithNullJsonObjectThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> AddConnectionLogEntry.fromJson(null))
                .withMessage("The jsonObject must not be null!")
                .withNoCause();
    }

    @Test
    public void fromJsonWithValidJsonObjectReturnsExpected() {
        final AddConnectionLogEntry addConnectionLogEntry = AddConnectionLogEntry.of(logEntry);

        assertThat(AddConnectionLogEntry.fromJson(addConnectionLogEntry.toJson())).isEqualTo(addConnectionLogEntry);
    }

    @Test
    public void fromJsonWithMissingJsonFieldThrowsException() {
        final JsonObject invalidAddConnectionLogEntryJsonObject = JsonObject.newBuilder().set("foo", "bar").build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> AddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject))
                .withMessageStartingWith("Failed to deserialize value of key <%s> as %s:",
                        AddConnectionLogEntry.JsonFields.LOG_ENTRY.getPointer(),
                        LogEntry.class.getName())
                .withCauseInstanceOf(JsonMissingFieldException.class);
    }

    @Test
    public void fromJsonWithInvalidLogEntryValueTypeThrowsException() {
        final JsonFieldDefinition<JsonObject> logEntryFieldDefinition = AddConnectionLogEntry.JsonFields.LOG_ENTRY;
        final JsonObject invalidAddConnectionLogEntryJsonObject = JsonObject.newBuilder()
                .set(logEntryFieldDefinition.getPointer(), "foo")
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> AddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject))
                .withMessageStartingWith("Failed to deserialize value of key <%s> as %s:",
                        logEntryFieldDefinition.getPointer(),
                        LogEntry.class.getName())
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void fromJsonWithCorruptedLogEntryValueThrowsException() {
        final JsonFieldDefinition<JsonObject> logEntryFieldDefinition = AddConnectionLogEntry.JsonFields.LOG_ENTRY;
        final JsonObject invalidLogEntryJsonObject = JsonFactory.newObjectBuilder(logEntry.toJson())
                .set(LogEntry.JsonFields.LEVEL, "foo")
                .build();
        final JsonObject invalidAddConnectionLogEntryJsonObject = JsonObject.newBuilder()
                .set(logEntryFieldDefinition, invalidLogEntryJsonObject)
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> AddConnectionLogEntry.fromJson(invalidAddConnectionLogEntryJsonObject))
                .withMessageStartingWith("Failed to deserialize value of key <%s> as %s:",
                        logEntryFieldDefinition.getPointer(),
                        LogEntry.class.getName())
                .withCauseInstanceOf(JsonParseException.class);
    }

}