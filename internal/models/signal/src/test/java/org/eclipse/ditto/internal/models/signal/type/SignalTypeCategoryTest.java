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
package org.eclipse.ditto.internal.models.signal.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link SignalTypeCategory}.
 */
public final class SignalTypeCategoryTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void toStringForEventReturnsExpected() {
        assertThat(SignalTypeCategory.EVENT).hasToString("events");
    }

    @Test
    public void getForStringWithNullStringThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> SignalTypeCategory.getForString(null))
                .withMessage("The signalTypeCategoryString must not be null!")
                .withNoCause();
    }

    @Test
    public void getForStringWithEmptyStringReturnsEmptyOptional() {
        assertThat(SignalTypeCategory.getForString("")).isEmpty();
    }

    @Test
    public void getForStringWithBlankStringReturnsEmptyOptional() {
        assertThat(SignalTypeCategory.getForString(" ")).isEmpty();
    }

    @Test
    public void toStringGetForStringRoundTripWorksAsExpected() {
        for (final var signalTypeCategory : SignalTypeCategory.values()) {
            assertThat(SignalTypeCategory.getForString(signalTypeCategory.toString()))
                    .as(signalTypeCategory.name())
                    .contains(signalTypeCategory);
        }
    }

}