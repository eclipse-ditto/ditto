/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.headers.conditional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Basic test for {@link ConditionalHeadersValidator}. The concrete functionality is tested in context of the using
 * service.
 */
public class ConditionalHeadersValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConditionalHeadersValidator.class, areImmutable(),
                provided(ConditionalHeadersValidator.ValidationSettings.class).isAlsoImmutable());
    }

    @Test
    public void creationFailsWithNullValidationSettings() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConditionalHeadersValidator.of(null));
    }
}
