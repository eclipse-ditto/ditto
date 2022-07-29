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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutableSubjectAnnouncement}.
 */
public final class ImmutableSubjectAnnouncementTest {

    private static final DittoDuration BEFORE_EXPIRY = DittoDuration.parseDuration("5m");
    private static final String BEFORE_EXPIRY_STRING = BEFORE_EXPIRY.toString();
    private static final JsonArray REQUESTED_ACKS = JsonArray.of("[\"integration:connection\"]");
    private static final DittoDuration ACKS_TIMEOUT = DittoDuration.parseDuration("10s");
    private static final DittoDuration RANDOMIZATION_INTERVAL = DittoDuration.parseDuration("5m");

    static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(SubjectAnnouncement.JsonFields.BEFORE_EXPIRY, BEFORE_EXPIRY_STRING)
            .set(SubjectAnnouncement.JsonFields.WHEN_DELETED, true)
            .set(SubjectAnnouncement.JsonFields.REQUESTED_ACKS_LABELS, REQUESTED_ACKS)
            .set(SubjectAnnouncement.JsonFields.REQUESTED_ACKS_TIMEOUT, ACKS_TIMEOUT.toString())
            .set(SubjectAnnouncement.JsonFields.RANDOMIZATION_INTERVAL, RANDOMIZATION_INTERVAL.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjectAnnouncement.class, areImmutable(),
                provided(DittoDuration.class, AcknowledgementRequest.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjectAnnouncement.class).verify();
    }

    @Test
    public void testToAndFromJson() {
        final List<AcknowledgementRequest> requestedAcks =
                Collections.singletonList(AcknowledgementRequest.parseAcknowledgementRequest("integration:connection"));
        final SubjectAnnouncement underTest =
                SubjectAnnouncement.of(BEFORE_EXPIRY, true, requestedAcks, ACKS_TIMEOUT, RANDOMIZATION_INTERVAL);

        final JsonObject subjectAnnouncementJson = underTest.toJson();
        final SubjectAnnouncement deserialized = SubjectAnnouncement.fromJson(subjectAnnouncementJson);

        assertThat(subjectAnnouncementJson).isEqualTo(KNOWN_JSON);
        assertThat(underTest).isEqualTo(deserialized);
    }

    @Test
    public void testToAndFromSubjectAnnouncementWithoutExpiryAndNotWhenDeletedAndRandomization() {
        final SubjectAnnouncement underTest = SubjectAnnouncement.of(null, false);
        final JsonObject emptyJson = underTest.toJson();
        final SubjectAnnouncement emptyAnnouncement = SubjectAnnouncement.fromJson(emptyJson);
        assertThat(emptyJson).containsExactly(
                JsonField.newInstance(SubjectAnnouncement.JsonFields.WHEN_DELETED.getPointer().getRoot()
                                .orElseThrow(NoSuchElementException::new),
                        JsonValue.of(false)));
        assertThat(emptyAnnouncement).isEqualTo(underTest);
    }

    @Test
    public void fromInvalidJson() {
        assertThatExceptionOfType(SubjectAnnouncementInvalidException.class).isThrownBy(() ->
                SubjectAnnouncement.fromJson(JsonObject.newBuilder()
                        .set(SubjectAnnouncement.JsonFields.BEFORE_EXPIRY, "3ms")
                        .build()
                )
        );

        assertThatExceptionOfType(SubjectAnnouncementInvalidException.class).isThrownBy(() ->
                SubjectAnnouncement.fromJson(JsonObject.newBuilder()
                        .set(SubjectAnnouncement.JsonFields.BEFORE_EXPIRY, "PT5h")
                        .build()
                )
        );

        assertThatExceptionOfType(SubjectAnnouncementInvalidException.class).isThrownBy(() ->
                SubjectAnnouncement.fromJson(JsonObject.newBuilder()
                        .set(SubjectAnnouncement.JsonFields.BEFORE_EXPIRY, "-2h")
                        .build()
                )
        );

        assertThatExceptionOfType(SubjectAnnouncementInvalidException.class).isThrownBy(() ->
                SubjectAnnouncement.fromJson(JsonObject.newBuilder()
                        .set(SubjectAnnouncement.JsonFields.REQUESTED_ACKS_TIMEOUT, "PT5h")
                        .build()
                )
        );

        assertThatExceptionOfType(SubjectAnnouncementInvalidException.class).isThrownBy(() ->
                SubjectAnnouncement.fromJson(JsonObject.newBuilder()
                        .set(SubjectAnnouncement.JsonFields.REQUESTED_ACKS_TIMEOUT, "-2h")
                        .build()
                )
        );

        assertThatExceptionOfType(SubjectAnnouncementInvalidException.class).isThrownBy(() ->
                SubjectAnnouncement.fromJson(JsonObject.newBuilder()
                        .set(SubjectAnnouncement.JsonFields.RANDOMIZATION_INTERVAL, "PT5h")
                        .build()
                )
        );

        assertThatExceptionOfType(SubjectAnnouncementInvalidException.class).isThrownBy(() ->
                SubjectAnnouncement.fromJson(JsonObject.newBuilder()
                        .set(SubjectAnnouncement.JsonFields.RANDOMIZATION_INTERVAL, "-2h")
                        .build()
                )
        );

    }
}
