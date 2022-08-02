/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableLogEntry}.
 */
public class ImmutableLogEntryTest {

    private static final String CORRELATION_ID = "a-correlation-id";
    private static final Instant TIMESTAMP = Instant.now();
    private static final LogCategory CATEGORY = LogCategory.SOURCE;
    private static final LogType TYPE = LogType.CONSUMED;
    private static final String MESSAGE = "Consumed the best transformation message in my life.";
    private static final LogLevel LEVEL = LogLevel.SUCCESS;
    private static final String ADDRESS = "an/address";
    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.poke:138");

    private static final LogEntry LOG_ENTRY =
            ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, CATEGORY, TYPE, LEVEL,
                    MESSAGE, ADDRESS, THING_ID)
                    .build();
    private static final JsonObject LOG_ENTRY_JSON = getLogEntryJson();

    @Test
    public void createInstanceWithNullCorrelationId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableLogEntry.getBuilder(null, TIMESTAMP, CATEGORY, TYPE, LEVEL, MESSAGE,
                        ADDRESS, THING_ID));
    }

    @Test
    public void createInstanceWithNullTimestamp() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableLogEntry.getBuilder(CORRELATION_ID, null, CATEGORY, TYPE, LEVEL, MESSAGE,
                        ADDRESS, THING_ID));
    }

    @Test
    public void createInstanceWithNullLogCategory() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, null, TYPE, LEVEL, MESSAGE,
                        ADDRESS, THING_ID));

    }

    @Test
    public void createInstanceWithNullLogType() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(
                        () -> ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, CATEGORY, null, LEVEL, MESSAGE,
                                ADDRESS, THING_ID));

    }

    @Test
    public void createInstanceWithNullMessage() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, CATEGORY, TYPE, LEVEL, null,
                        ADDRESS, THING_ID));

    }

    @Test
    public void createInstanceWithNullLogLevel() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, CATEGORY, TYPE, null, MESSAGE,
                        ADDRESS, THING_ID));

    }

    @Test
    public void createInstanceWithNullAddress() {
        final LogEntry logEntry = ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, CATEGORY, TYPE, LEVEL,
                MESSAGE, null, THING_ID)
                .build();
        assertThat(logEntry.getAddress()).isEmpty();
    }

    @Test
    public void createInstanceWithNullThingId() {
        final LogEntry logEntry = ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, CATEGORY, TYPE, LEVEL,
                MESSAGE, ADDRESS, null)
                .build();
        assertThat(logEntry.getEntityId()).isEmpty();
    }

    @Test
    public void logEntryWithMissingThingIdAndAddress() {
        final LogEntry logEntry = ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP, CATEGORY, TYPE, LEVEL,
                MESSAGE, null, null)
                .build();
        final JsonObject json = logEntry.toJson();

        assertThat(json.getValue(LogEntry.JsonFields.ADDRESS.getPointer())).isEmpty();
        assertThat(json.getValue(LogEntry.JsonFields.ENTITY_ID.getPointer())).isEmpty();
    }

    @Test
    public void jsonWithMissingThingIdAndAddress() {
        final JsonObject json = getLogEntryJson()
                .remove(LogEntry.JsonFields.ADDRESS.getPointer().toString())
                .remove(LogEntry.JsonFields.ENTITY_ID.getPointer().toString());
        final LogEntry logEntry = ImmutableLogEntry.fromJson(json);

        assertThat(logEntry.getAddress()).isEmpty();
        assertThat(logEntry.getEntityId()).isEmpty();
    }

    @Test
    public void verifyBuilder() {
        final LogEntryBuilder builder = ImmutableLogEntry.getBuilder("any", Instant.now().minusSeconds(123),
                LogCategory.RESPONSE, LogType.DISPATCHED, LogLevel.FAILURE, "other message");
        final LogEntry builtEntry = builder
                .correlationId(CORRELATION_ID)
                .timestamp(TIMESTAMP)
                .logCategory(CATEGORY)
                .logType(TYPE)
                .logLevel(LEVEL)
                .message(MESSAGE)
                .address(ADDRESS)
                .entityId(THING_ID)
                .build();
        assertThat(builtEntry).isEqualTo(LOG_ENTRY);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = LOG_ENTRY.toJson();
        assertThat(actual).isEqualTo(LOG_ENTRY_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final LogEntry actual = ImmutableLogEntry.fromJson(LOG_ENTRY_JSON);
        assertThat(actual).isEqualTo(LOG_ENTRY);
    }

    @Test
    public void fromJsonWithIllegalDate() {
        final JsonObject json = LOG_ENTRY_JSON.set(LogEntry.JsonFields.TIMESTAMP, "not-a-date");
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> ImmutableLogEntry.fromJson(json))
                .withMessage("The JSON object's field <%s> is not in ISO-8601 format as expected!",
                        LogEntry.JsonFields.TIMESTAMP.getPointer());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableLogEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableLogEntry.class, areImmutable(),
                provided(LogType.class, EntityId.class).areAlsoImmutable());
    }

    private static JsonObject getLogEntryJson() {
        return JsonFactory.newObjectBuilder()
                .set(LogEntry.JsonFields.CORRELATION_ID, CORRELATION_ID)
                .set(LogEntry.JsonFields.TIMESTAMP, TIMESTAMP.toString())
                .set(LogEntry.JsonFields.CATEGORY, CATEGORY.getName())
                .set(LogEntry.JsonFields.TYPE, TYPE.getType())
                .set(LogEntry.JsonFields.MESSAGE, MESSAGE)
                .set(LogEntry.JsonFields.LEVEL, LEVEL.getLevel())
                .set(LogEntry.JsonFields.ADDRESS, ADDRESS)
                .set(LogEntry.JsonFields.ENTITY_ID, THING_ID.toString())
                .set(LogEntry.JsonFields.ENTITY_TYPE, THING_ID.getEntityType().toString())
                .build();
    }

}
