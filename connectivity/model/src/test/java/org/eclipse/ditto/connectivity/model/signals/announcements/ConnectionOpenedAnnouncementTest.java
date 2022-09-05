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
 * Unit test for {@link ConnectionOpenedAnnouncement}.
 */
public final class ConnectionOpenedAnnouncementTest {

    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().putHeader("foo", "bar").build();
    private static final ConnectionId CONNECTION_ID = ConnectionId.of("hello");
    private static final Instant OPENED_AT = Instant.now();

    @Test
    public void createNewInstance() {
        final ConnectionOpenedAnnouncement underTest =
                ConnectionOpenedAnnouncement.of(CONNECTION_ID, OPENED_AT, HEADERS);

        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(CONNECTION_ID);
        assertThat(underTest.getOpenedAt()).isEqualTo(OPENED_AT);
        assertThat(underTest.getName()).isEqualTo("opened");
        assertThat(underTest.getResourceType()).isEqualTo(ConnectivityConstants.ENTITY_TYPE.toString());
    }

    @Test
    public void createNewInstanceWithInvalidArguments() {
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> connectionId")
                .isThrownBy(() -> ConnectionOpenedAnnouncement.of(null, OPENED_AT, HEADERS));
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> openedAt")
                .isThrownBy(() -> ConnectionOpenedAnnouncement.of(CONNECTION_ID, null, HEADERS));
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> dittoHeaders")
                .isThrownBy(() -> ConnectionOpenedAnnouncement.of(CONNECTION_ID, OPENED_AT, null));
    }

    @Test
    public void toAndFromJson() {
        final ConnectionOpenedAnnouncement original =
                ConnectionOpenedAnnouncement.of(CONNECTION_ID, OPENED_AT, HEADERS);
        final JsonObject serialized = original.toJson();
        final ConnectionOpenedAnnouncement deserialized = ConnectionOpenedAnnouncement.fromJson(serialized, HEADERS);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    public void fromJsonWithInvalidArguments() {
        final JsonObject serialized = ConnectionOpenedAnnouncement.of(CONNECTION_ID, OPENED_AT, HEADERS).toJson();

        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> jsonObject")
                .isThrownBy(() -> ConnectionOpenedAnnouncement.fromJson(null, HEADERS));
        assertThatExceptionOfType(NullPointerException.class)
                .describedAs("<null> dittoHeaders")
                .isThrownBy(() -> ConnectionOpenedAnnouncement.fromJson(serialized, null));
    }

    @Test
    public void fromJsonWithInvalidContent() {
        final JsonObject serialized = ConnectionOpenedAnnouncement.of(CONNECTION_ID, OPENED_AT, HEADERS).toJson();

        assertThatExceptionOfType(JsonParseException.class)
                .describedAs("invalid connectionId")
                .isThrownBy(() -> ConnectionOpenedAnnouncement.fromJson(
                        serialized.setValue("connectionId", ""), HEADERS))
                .withCauseInstanceOf(ConnectionIdInvalidException.class);
        assertThatExceptionOfType(JsonParseException.class)
                .describedAs("invalid openedAt")
                .isThrownBy(() -> ConnectionOpenedAnnouncement.fromJson(
                        serialized.setValue("openedAt", String.valueOf(Instant.now().getEpochSecond())), HEADERS))
                .withCauseInstanceOf(DateTimeParseException.class);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(ConnectionOpenedAnnouncement.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(ConnectionOpenedAnnouncement.class, areImmutable());
    }

}
