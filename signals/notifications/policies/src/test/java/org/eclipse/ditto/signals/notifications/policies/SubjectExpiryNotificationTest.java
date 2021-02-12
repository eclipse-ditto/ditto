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
package org.eclipse.ditto.signals.notifications.policies;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SubjectExpiryNotification}.
 */
public final class SubjectExpiryNotificationTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubjectExpiryNotification.class,
                areImmutable(),
                provided(PolicyId.class).areAlsoImmutable(),
                assumingFields("expiringSubjects").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubjectExpiryNotification.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        SubjectExpiryNotification.of(null, Instant.EPOCH, Collections.emptyList(), DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullExpiry() {
        SubjectExpiryNotification.of(PolicyId.dummy(), null, Collections.emptyList(), DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullExpiringSubjects() {
        SubjectExpiryNotification.of(PolicyId.dummy(), Instant.EPOCH, null, DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDittoHeaders() {
        SubjectExpiryNotification.of(PolicyId.dummy(), Instant.EPOCH, Collections.emptyList(), null);
    }

    @Test
    public void toJsonReturnsExpected() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubjectExpiryNotification underTest = SubjectExpiryNotification.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(AuthorizationSubject.newInstance("ditto:ditto")),
                dittoHeaders
        );

        final JsonObject expected = JsonObject.of(String.format("{\n" +
                "  \"type\": \"policies.notification:subject.expiry\",\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"expiry\": \"%s\",\n" +
                "  \"expiringSubjects\": [\"ditto:ditto\"]\n" +
                "}", expiry.toString()));

        assertThat(underTest.toJson()).isEqualToIgnoringFieldDefinitions(expected);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();

        final JsonObject json = JsonObject.of(String.format("{\n" +
                "  \"type\": \"policies.notification:subject.expiry\",\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"expiry\": \"%s\",\n" +
                "  \"expiringSubjects\": [\"ditto:ditto\"]\n" +
                "}", expiry.toString()));

        final SubjectExpiryNotification underTest = SubjectExpiryNotification.fromJson(json, dittoHeaders);

        final SubjectExpiryNotification expected = SubjectExpiryNotification.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(AuthorizationSubject.newInstance("ditto:ditto")),
                dittoHeaders
        );

        assertThat(underTest).isEqualTo(expected);
    }
}
