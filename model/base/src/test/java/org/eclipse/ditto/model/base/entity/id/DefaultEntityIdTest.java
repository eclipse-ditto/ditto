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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultEntityId}.
 */
public final class DefaultEntityIdTest {

    private static final EntityType THING_TYPE = EntityType.of("thing");

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultEntityId.class, areImmutable(), provided(EntityType.class).isAlsoImmutable());
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
                .isThrownBy(() -> DefaultEntityId.of(THING_TYPE, null))
                .withMessage("The entityId must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceFromEmptyString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DefaultEntityId.of(THING_TYPE, ""))
                .withMessage("The argument 'entityId' must not be empty!")
                .withNoCause();
    }

    @Test
    public void getInstanceFromDefaultEntityIdReturnsSame() {
        final DefaultEntityId underTest = DefaultEntityId.generateRandom(THING_TYPE);

        assertThat((CharSequence) DefaultEntityId.of(THING_TYPE, underTest)).isSameAs(underTest);
    }


}
