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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Test for {@link RetrieveConnectionLogsResponse}.
 */
public final class RetrieveConnectionLogsResponseTest {

    private static final DittoHeaders EMPTY_HEADERS = DittoHeaders.empty();
    private static final Instant ENABLED_SINCE = Instant.now().minus(15, ChronoUnit.MINUTES);
    private static final Instant ENABLED_UNTIL = Instant.now().plus(1, ChronoUnit.DAYS);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ConnectivityCommandResponse.JsonFields.STATUS, 200)
            .set(ConnectivityCommandResponse.JsonFields.TYPE, RetrieveConnectionLogsResponse.TYPE)
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, TestConstants.ID.toString())
            .set(RetrieveConnectionLogsResponse.JsonFields.ENABLED_SINCE, ENABLED_SINCE.toString())
            .set(RetrieveConnectionLogsResponse.JsonFields.ENABLED_UNTIL, ENABLED_UNTIL.toString())
            .set(RetrieveConnectionLogsResponse.JsonFields.CONNECTION_LOGS, TestConstants.Logs.Json.ENTRIES_JSON)
            .build();

    @Test
    public void retrieveInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionLogsResponse.of(null,
                        TestConstants.Logs.ENTRIES,
                        ENABLED_SINCE,
                        ENABLED_UNTIL,
                        EMPTY_HEADERS))
                .withMessage("The %s must not be null!", "connectionId")
                .withNoCause();
    }

    @Test
    public void retrieveInstanceWithNullLogs() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionLogsResponse.of(TestConstants.ID,
                        null,
                        ENABLED_SINCE,
                        ENABLED_UNTIL,
                        EMPTY_HEADERS))
                .withMessage("The %s must not be null!", "connectionLogs")
                .withNoCause();
    }

    @Test
    public void fromJsonWithStringReturnsExpected() {
        final RetrieveConnectionLogsResponse expected = RetrieveConnectionLogsResponse.of(TestConstants.ID,
                TestConstants.Logs.ENTRIES,
                ENABLED_SINCE,
                ENABLED_UNTIL,
                EMPTY_HEADERS);

        final RetrieveConnectionLogsResponse actual =
                RetrieveConnectionLogsResponse.fromJson(KNOWN_JSON.toString(), EMPTY_HEADERS);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionLogsResponse expected = RetrieveConnectionLogsResponse.of(TestConstants.ID,
                TestConstants.Logs.ENTRIES,
                ENABLED_SINCE,
                ENABLED_UNTIL,
                EMPTY_HEADERS);

        final RetrieveConnectionLogsResponse actual =
                RetrieveConnectionLogsResponse.fromJson(KNOWN_JSON, EMPTY_HEADERS);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromJsonWithIllegalDate() {
        final JsonObject json = KNOWN_JSON.toBuilder()
                .set(RetrieveConnectionLogsResponse.JsonFields.ENABLED_SINCE, "not-a-date")
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> RetrieveConnectionLogsResponse.fromJson(json, EMPTY_HEADERS))
                .withMessageContaining("The JSON object's field <%s> is not in ISO-8601 format as expected!",
                        RetrieveConnectionLogsResponse.JsonFields.ENABLED_SINCE.getPointer());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = RetrieveConnectionLogsResponse.of(TestConstants.ID,
                TestConstants.Logs.ENTRIES,
                ENABLED_SINCE,
                ENABLED_UNTIL,
                EMPTY_HEADERS).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void merge() {
        final RetrieveConnectionLogsResponse firstResponse = RetrieveConnectionLogsResponse.of(TestConstants.ID,
                TestConstants.Logs.ENTRIES,
                ENABLED_SINCE,
                ENABLED_UNTIL,
                EMPTY_HEADERS);
        final DittoHeaders secondHeaders = DittoHeaders.newBuilder().correlationId("hey there").build();
        final Collection<LogEntry> secondLogEntries = Collections.singleton(ConnectivityModelFactory.newLogEntryBuilder(
                "hey there",
                Instant.now(),
                LogCategory.TARGET,
                LogType.MAPPED,
                LogLevel.FAILURE,
                "oh boy, that wen't wrong"
        ).build());
        final RetrieveConnectionLogsResponse secondResponse = RetrieveConnectionLogsResponse.of(TestConstants.ID,
                secondLogEntries,
                Instant.now().minusSeconds(789),
                Instant.now().plusSeconds(12312),
                secondHeaders);


        final RetrieveConnectionLogsResponse merged =
                RetrieveConnectionLogsResponse.mergeRetrieveConnectionLogsResponse(firstResponse, secondResponse);

        final Collection<LogEntry> expectedEntries = new ArrayList<>(TestConstants.Logs.ENTRIES);
        expectedEntries.addAll(secondLogEntries);

        assertThat((CharSequence) merged.getEntityId()).isEqualTo(TestConstants.ID);
        assertThat(merged.getEnabledSince()).contains(ENABLED_SINCE);
        assertThat(merged.getEnabledUntil()).contains(ENABLED_UNTIL);
        assertThat(merged.getConnectionLogs()).containsOnlyElementsOf(expectedEntries);
        assertThat(merged.getConnectionLogs()).hasSameSizeAs(expectedEntries);
    }

    @Test
    public void testEqualsAndHashCode() {
        EqualsVerifier.forClass(RetrieveConnectionLogsResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveConnectionLogsResponse.class,
                areImmutable(),
                provided(LogEntry.class, ConnectionId.class).areAlsoImmutable());
    }

}
