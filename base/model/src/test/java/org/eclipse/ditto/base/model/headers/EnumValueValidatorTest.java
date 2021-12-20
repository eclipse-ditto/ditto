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
package org.eclipse.ditto.base.model.headers;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.junit.Test;

/**
 * Unit tests for {@link EnumValueValidator}.
 */
public final class EnumValueValidatorTest {

    private static final EnumValueValidator underTest = EnumValueValidator.getInstance(FancyTestEnum.values());

    private static final DittoHeaderDefinition KNOWN_HEADER_DEFINITION =
            DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY;

    @Test
    public void assertImmutability() {
        assertInstancesOf(EnumValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, FancyTestEnum.BAZ_BAR.s))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToInitializeWithNullEnumValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> EnumValueValidator.getInstance(null))
                .withMessage("The enumValues must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToInitializeWithEmptyEnumValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EnumValueValidator.getInstance(new Enum<?>[] {}))
                .withMessage("The enumValues must not be empty!")
                .withNoCause();
    }

    @Test
    public void ensureValidEnumValuesDoNotThrowException() {
        assertThatNoException()
                .isThrownBy(() ->
                        underTest.validateValue(KNOWN_HEADER_DEFINITION, "foo")
                );
        assertThatNoException()
                .isThrownBy(() ->
                        underTest.validateValue(KNOWN_HEADER_DEFINITION, FancyTestEnum.BAZ_BAR.s)
                );
    }

    @Test
    public void invalidEnumValuesThrowException() {
        assertThatThrownBy(() ->
                underTest.validateValue(KNOWN_HEADER_DEFINITION, "what")
        )
                .isInstanceOf(DittoHeaderInvalidException.class)
                .hasMessage("The value 'what' of the header 'live-channel-timeout-strategy' is not a valid enum value of " +
                        "type 'FancyTestEnum'.")
                .matches(ex -> ((DittoHeaderInvalidException) ex).getDescription()
                        .filter(desc -> desc.equals("The value must be one of: <foo|bar|baz-bar>."))
                        .isPresent(), "Contains the expected description");
    }


    enum FancyTestEnum {
        FOO("foo"),
        BAR("bar"),
        BAZ_BAR("baz-bar");

        private final String s;

        FancyTestEnum(final String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }
    }
}