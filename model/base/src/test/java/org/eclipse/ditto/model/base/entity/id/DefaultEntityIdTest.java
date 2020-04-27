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
package org.eclipse.ditto.model.base.entity.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultEntityId}.
 */
public final class DefaultEntityIdTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultEntityId.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultEntityId.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceFromNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultEntityId.of(null))
                .withMessage("The entityId must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceFromEmptyString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DefaultEntityId.of(""))
                .withMessage("The argument 'entityId' must not be empty!")
                .withNoCause();
    }

    @Test
    public void getInstanceFromDefaultEntityIdReturnsSame() {
        final DefaultEntityId underTest = DefaultEntityId.generateRandom();

        assertThat((CharSequence) DefaultEntityId.of(underTest)).isSameAs(underTest);
    }

    @Test
    public void dummyEntityIdIsDummy() {
        final DefaultEntityId dummy = DefaultEntityId.dummy();

        assertThat(dummy.isDummy()).isTrue();
    }

}