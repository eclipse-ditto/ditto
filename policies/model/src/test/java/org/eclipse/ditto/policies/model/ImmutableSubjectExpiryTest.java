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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableSubjectExpiry}.
 */
public final class ImmutableSubjectExpiryTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjectExpiry.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjectExpiry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void wellKnownIso8601ExpiryProducesExpectedInstant() {
        final String expiry = "2020-11-23T08:48:46Z";
        final SubjectExpiry subjectExpiry = ImmutableSubjectExpiry.of(expiry);
        assertThat(subjectExpiry.getTimestamp()).isEqualTo(expiry);
    }

    @Test
    public void toStringNonIso8601ThrowsDateTimeFormatException() {
        final String expiry = "Foo";
        assertThatExceptionOfType(SubjectExpiryInvalidException.class)
                .isThrownBy(() -> ImmutableSubjectExpiry.of(expiry));
    }

}
