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
package org.eclipse.ditto.model.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoDuration;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutableSubjectAnnouncement}.
 */
public final class ImmutableSubjectAnnouncementTest {

    private static final DittoDuration BEFORE_EXPIRY = DittoDuration.parseDuration("5m");
    private static final String BEFORE_EXPIRY_STRING = BEFORE_EXPIRY.toString();

    static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(SubjectAnnouncement.JsonFields.BEFORE_EXPIRY, BEFORE_EXPIRY_STRING)
            .set(SubjectAnnouncement.JsonFields.WHEN_DELETED, true)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjectAnnouncement.class, areImmutable(),
                provided(DittoDuration.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjectAnnouncement.class).verify();
    }

    @Test
    public void testToAndFromJson() {
        final SubjectAnnouncement underTest = SubjectAnnouncement.of(BEFORE_EXPIRY, true);

        final JsonObject subjectAnnouncementJson = underTest.toJson();
        final SubjectAnnouncement deserialized = SubjectAnnouncement.fromJson(subjectAnnouncementJson);

        assertThat(subjectAnnouncementJson).isEqualTo(KNOWN_JSON);
        assertThat(underTest).isEqualTo(deserialized);
    }

    @Test
    public void testToAndFromEmptyJson() {
        final SubjectAnnouncement underTest = SubjectAnnouncement.empty();
        final JsonObject emptyJson = underTest.toJson();
        final SubjectAnnouncement emptyAnnouncement = SubjectAnnouncement.fromJson(emptyJson);
        assertThat(emptyJson).isEmpty();
        assertThat(emptyAnnouncement).isEqualTo(underTest);
        assertThat(underTest.isEmpty()).isTrue();
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
    }
}
