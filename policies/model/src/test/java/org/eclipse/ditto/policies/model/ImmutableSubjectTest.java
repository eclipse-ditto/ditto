/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSubject}.
 */
public final class ImmutableSubjectTest {

    private static final String KNOWN_SUBJECT_TYPE = "custom";
    private static final Instant KNOWN_SUBJECT_EXPIRY = Instant.now();
    private static final String KNOWN_SUBJECT_EXPIRY_STR = KNOWN_SUBJECT_EXPIRY.toString();
    private static final JsonObject KNOWN_SUBJECT_ANNOUNCEMENT_JSON = ImmutableSubjectAnnouncementTest.KNOWN_JSON;
    private static final JsonObject KNOWN_SUBJECT_JSON = JsonObject.newBuilder()
            .set(Subject.JsonFields.TYPE, KNOWN_SUBJECT_TYPE)
            .set(Subject.JsonFields.EXPIRY, KNOWN_SUBJECT_EXPIRY_STR)
            .set(Subject.JsonFields.ANNOUNCEMENT, KNOWN_SUBJECT_ANNOUNCEMENT_JSON)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubject.class,
                areImmutable(),
                provided(SubjectId.class, SubjectType.class, SubjectExpiry.class, SubjectAnnouncement.class)
                        .areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubject.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final Subject subject =
                ImmutableSubject.of(SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself"));

        final JsonObject subjectJson = subject.toJson();
        final Subject subject1 = ImmutableSubject.fromJson(SubjectIssuer.GOOGLE + ":myself", subjectJson);

        assertThat(subject).isEqualTo(subject1);
    }

    @Test
    public void testToAndFromJsonWithAllFields() {
        final Subject subject = ImmutableSubject.of(SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself"),
                SubjectType.newInstance(KNOWN_SUBJECT_TYPE),
                SubjectExpiry.newInstance(KNOWN_SUBJECT_EXPIRY_STR),
                SubjectAnnouncement.of(DittoDuration.parseDuration("5m"), true,
                        Collections.singletonList(
                                AcknowledgementRequest.parseAcknowledgementRequest("integration:connection")),
                        DittoDuration.parseDuration("10s"), DittoDuration.parseDuration("5m")
                ));

        final Subject subject1 = ImmutableSubject.fromJson(SubjectIssuer.GOOGLE + ":myself",
                KNOWN_SUBJECT_JSON);

        assertThat(subject).isEqualTo(subject1);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectIssuer() {
        Subject.newInstance(null, TestConstants.Policy.SUBJECT_ID_SUBJECT, TestConstants.Policy.SUBJECT_TYPE);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubject() {
        Subject.newInstance(SubjectIssuer.GOOGLE, null, TestConstants.Policy.SUBJECT_TYPE);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectId() {
        Subject.newInstance((SubjectId) null, TestConstants.Policy.SUBJECT_TYPE);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectType() {
        Subject.newInstance(TestConstants.Policy.SUBJECT_ID, null);
    }

    @Test
    public void createSubjectSuccess() {
        final SubjectType customType = PoliciesModelFactory.newSubjectType("custom");
        final Subject underTest = Subject.newInstance(TestConstants.Policy.SUBJECT_ID, customType);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Policy.SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(customType);
    }

    @Test
    public void createSubjectWithUnknownTypeSuccess() {
        final Subject underTest = Subject.newInstance(TestConstants.Policy.SUBJECT_ID);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Policy.SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(SubjectType.GENERATED);
    }

    @Test
    public void createSubjectWithIssuerSuccess() {
        final Subject underTest =
                Subject.newInstance(TestConstants.Policy.SUBJECT_ISSUER, TestConstants.Policy.SUBJECT_ID_SUBJECT,
                        TestConstants.Policy.SUBJECT_TYPE);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Policy.SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(TestConstants.Policy.SUBJECT_TYPE);
    }

    @Test
    public void subjectWithIssuerEqualsSubjectAndIssuer() {
        final Subject subjectAndIssuer =
                Subject.newInstance(TestConstants.Policy.SUBJECT_ISSUER, TestConstants.Policy.SUBJECT_ID_SUBJECT,
                        TestConstants.Policy.SUBJECT_TYPE);
        final Subject subjectWithIssuer = Subject.newInstance(
                TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE);

        assertThat(subjectWithIssuer).isEqualTo(subjectAndIssuer);
    }

}
