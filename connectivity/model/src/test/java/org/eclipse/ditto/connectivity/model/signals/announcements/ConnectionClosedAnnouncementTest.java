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

package org.eclipse.ditto.connectivity.model.signals.announcements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ConnectionClosedAnnouncement}.
 */
public final class ConnectionClosedAnnouncementTest {

    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().putHeader("foo", "bar").build();
    private static final ConnectionId CONNECTION_ID = ConnectionId.of("hello");
    private static final Instant CLOSED_AT = Instant.now();

    @Test
    public void createNewInstance() {
        final ConnectionClosedAnnouncement underTest =
                ConnectionClosedAnnouncement.of(CONNECTION_ID, CLOSED_AT, HEADERS);

        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(CONNECTION_ID);
        assertThat(underTest.getClosedAt()).isEqualTo(CLOSED_AT);
        assertThat(underTest.getName()).isEqualTo("closed");
        assertThat(underTest.getResourceType()).isEqualTo(ConnectivityConstants.ENTITY_TYPE.toString());
    }

    @Test
    public void createNewInstanceWithInvalidArguments() {
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> connectionId")
                .isThrownBy(() -> ConnectionClosedAnnouncement.of(null, CLOSED_AT, HEADERS));
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> closedAt")
                .isThrownBy(() -> ConnectionClosedAnnouncement.of(CONNECTION_ID, null, HEADERS));
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> dittoHeaders")
                .isThrownBy(() -> ConnectionClosedAnnouncement.of(CONNECTION_ID, CLOSED_AT, null));
    }

    @Test
    public void toAndFromJson() {
        final ConnectionClosedAnnouncement original =
                ConnectionClosedAnnouncement.of(CONNECTION_ID, CLOSED_AT, HEADERS);
        final JsonObject serialized = original.toJson();
        final ConnectionClosedAnnouncement deserialized = ConnectionClosedAnnouncement.fromJson(serialized, HEADERS);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    public void fromJsonWithInvalidArguments() {
        final JsonObject serialized = ConnectionClosedAnnouncement.of(CONNECTION_ID, CLOSED_AT, HEADERS).toJson();

        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> jsonObject")
                .isThrownBy(() -> ConnectionClosedAnnouncement.fromJson(null, HEADERS));
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> dittoHeaders")
                .isThrownBy(() -> ConnectionClosedAnnouncement.fromJson(serialized, null));
    }

    @Test
    public void fromJsonWithInvalidContent() {
        final JsonObject serialized = ConnectionClosedAnnouncement.of(CONNECTION_ID, CLOSED_AT, HEADERS).toJson();

        assertThatExceptionOfType(JsonParseException.class)
                .describedAs("invalid connectionId")
                .isThrownBy(() -> ConnectionClosedAnnouncement.fromJson(
                        serialized.setValue("connectionId", ""), HEADERS))
                .withCauseInstanceOf(ConnectionIdInvalidException.class);
        assertThatExceptionOfType(JsonParseException.class)
                .describedAs("invalid closedAt")
                .isThrownBy(() -> ConnectionClosedAnnouncement.fromJson(
                        serialized.setValue("closedAt", String.valueOf(Instant.now().getEpochSecond())), HEADERS))
                .withCauseInstanceOf(DateTimeParseException.class);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(ConnectionClosedAnnouncement.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(ConnectionClosedAnnouncement.class, areImmutable());
    }

}
