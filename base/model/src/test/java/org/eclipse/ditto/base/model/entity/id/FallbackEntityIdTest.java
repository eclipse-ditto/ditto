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
package org.eclipse.ditto.base.model.entity.id;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.entity.id.FallbackEntityId}.
 */
public final class FallbackEntityIdTest {

    private static final EntityType UNKNOWN_TYPE = EntityType.of("unknown");

    @Test
    public void assertImmutability() {
        assertInstancesOf(FallbackEntityId.class, areImmutable(), provided(EntityType.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(FallbackEntityId.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceFromNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> FallbackEntityId.of(UNKNOWN_TYPE, null))
                .withMessage("The entityId must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceFromEmptyString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> FallbackEntityId.of(UNKNOWN_TYPE, ""))
                .withMessage("The argument 'entityId' must not be empty!")
                .withNoCause();
    }

}
