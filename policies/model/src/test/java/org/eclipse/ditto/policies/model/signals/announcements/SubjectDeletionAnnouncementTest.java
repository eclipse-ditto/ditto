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
package org.eclipse.ditto.policies.model.signals.announcements;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement}.
 */
public final class SubjectDeletionAnnouncementTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubjectDeletionAnnouncement.class,
                areImmutable(),
                provided(PolicyId.class).areAlsoImmutable(),
                assumingFields("subjectIds").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubjectDeletionAnnouncement.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        SubjectDeletionAnnouncement.of(null, Instant.EPOCH, Collections.emptyList(), DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullExpiry() {
        SubjectDeletionAnnouncement.of(PolicyId.of("test:policyid"), null, Collections.emptyList(),
                DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullExpiringSubjects() {
        SubjectDeletionAnnouncement.of(PolicyId.of("test:policyid"), Instant.EPOCH, null, DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDittoHeaders() {
        SubjectDeletionAnnouncement.of(PolicyId.of("test:policyid"), Instant.EPOCH, Collections.emptyList(), null);
    }

    @Test
    public void toJsonReturnsExpected() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubjectDeletionAnnouncement underTest = SubjectDeletionAnnouncement.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(SubjectId.newInstance("ditto:ditto")),
                dittoHeaders
        );

        final JsonObject expected = JsonObject.of(String.format("{\n" +
                "  \"type\": \"policies.announcements:subjectDeletion\",\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"deleteAt\": \"%s\",\n" +
                "  \"subjectIds\": [\"ditto:ditto\"]\n" +
                "}", expiry.toString()));

        assertThat(underTest.toJson()).isEqualToIgnoringFieldDefinitions(expected);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();

        final JsonObject json = JsonObject.of(String.format("{\n" +
                "  \"type\": \"policies.announcements:subjectDeletion\",\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"deleteAt\": \"%s\",\n" +
                "  \"subjectIds\": [\"ditto:ditto\"]\n" +
                "}", expiry.toString()));

        final SubjectDeletionAnnouncement underTest = SubjectDeletionAnnouncement.fromJson(json, dittoHeaders);

        final SubjectDeletionAnnouncement expected = SubjectDeletionAnnouncement.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(SubjectId.newInstance("ditto:ditto")),
                dittoHeaders
        );

        assertThat(underTest).isEqualTo(expected);
    }
}
