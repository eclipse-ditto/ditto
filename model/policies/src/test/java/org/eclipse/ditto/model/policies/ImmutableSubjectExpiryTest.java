/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoDuration;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableSubjectExpiry}.
 */
public final class ImmutableSubjectExpiryTest {

    private static final String TIMESTAMP = "2020-11-23T08:48:46Z";
    private static final String NOTIFY_BEFORE = "2h";

    private static final JsonObject JSON_WITH_NOTIFY_BEFORE = JsonObject.newBuilder()
            .set(SubjectExpiry.JsonFields.TIMESTAMP, TIMESTAMP)
            .set(SubjectExpiry.JsonFields.NOTIFY_BEFORE, NOTIFY_BEFORE)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjectExpiry.class, areImmutable(),
                provided(DittoDuration.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjectExpiry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void wellKnownIso8601ExpiryProducesExpectedInstant() {
        final String expiry = TIMESTAMP;
        final SubjectExpiry subjectExpiry = ImmutableSubjectExpiry.of(expiry);
        assertThat(subjectExpiry.getTimestamp()).isEqualTo(expiry);
    }

    @Test
    public void nonIso8601ThrowsSubjectExpiryInvalidException() {
        final String expiry = "Foo";
        assertThatExceptionOfType(SubjectExpiryInvalidException.class)
                .isThrownBy(() -> ImmutableSubjectExpiry.of(expiry));
    }

    @Test
    public void unsupportedNotifyBeforeTimeUnitThrowsSubjectExpiryInvalidException() {
        assertThatExceptionOfType(SubjectExpiryInvalidException.class)
                .isThrownBy(() -> ImmutableSubjectExpiry.parseAndValidate(TIMESTAMP, "2ms"));
    }

    @Test
    public void toJsonWithNotifyBefore() {
        final SubjectExpiry underTest = ImmutableSubjectExpiry.parseAndValidate(TIMESTAMP, NOTIFY_BEFORE);
        assertThat(underTest.toJson()).isEqualTo(JSON_WITH_NOTIFY_BEFORE);
    }

    @Test
    public void toJsonWithoutNotifyBefore() {
        final SubjectExpiry underTest = ImmutableSubjectExpiry.of(TIMESTAMP);
        assertThat(underTest.toJson()).isEqualTo(JsonValue.of(TIMESTAMP));
    }

    @Test
    public void fromJsonWithNotifyBefore() {
        final Object underTest = ImmutableSubjectExpiry.fromJson(JSON_WITH_NOTIFY_BEFORE);
        assertThat(underTest).isEqualTo(ImmutableSubjectExpiry.parseAndValidate(TIMESTAMP, NOTIFY_BEFORE));
    }

    @Test
    public void fromJsonWithoutNotifyBefore() {
        final Object underTest = ImmutableSubjectExpiry.fromJson(JsonValue.of(TIMESTAMP));
        assertThat(underTest).isEqualTo(ImmutableSubjectExpiry.of(TIMESTAMP));
    }

}
